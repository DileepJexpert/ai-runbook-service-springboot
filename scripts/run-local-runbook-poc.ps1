param(
    [string]$RunbookServiceDir = (Resolve-Path "$PSScriptRoot\..").Path,
    [string]$TargetRepo = "",
    [string]$ServiceId = "sample-service",
    [string]$InputDataPath = "",
    [string]$InputEvidencePath = "",
    [string]$InputSecurityFindingsPath = "",
    [string]$ArtifactsRoot = (Join-Path (Resolve-Path "$PSScriptRoot\..").Path "build\runbook-artifacts"),
    [string]$BaseUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 120,
    [int]$JobTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message"
}

function To-ForwardSlashPath([string]$Path) {
    return ($Path -replace '\\', '/')
}

function Test-RunbookHealth {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 3
        return ($health.status -eq "UP")
    }
    catch {
        return $false
    }
}

Write-Step "Checking required files and folders"

$requiredPaths = @(
    $RunbookServiceDir,
    $TargetRepo,
    $InputDataPath,
    $InputEvidencePath
)

foreach ($path in $requiredPaths) {
    if (-not (Test-Path $path)) {
        throw "Required path not found: $path"
    }
}

if (-not (Test-Path $ArtifactsRoot)) {
    New-Item -ItemType Directory -Path $ArtifactsRoot -Force | Out-Null
}

# Show scan status if present. PARTIAL is allowed for this render-only POC.
try {
    $inputJson = Get-Content $InputDataPath -Raw | ConvertFrom-Json
    $scanStatus = $null

    if ($inputJson.generator -and $inputJson.generator.scanStatus) {
        $scanStatus = $inputJson.generator.scanStatus
    }

    if ($scanStatus) {
        Write-Host "Input scanStatus: $scanStatus"
        if ($scanStatus -eq "PARTIAL") {
            Write-Host "NOTE: PARTIAL is acceptable for this local render-only POC. This script will NOT publish."
        }
    }
}
catch {
    Write-Warning "Could not read scanStatus from runbook-data.json. Spring Boot validation will decide validity."
}

Write-Step "Checking Spring Boot runbook service"

if (-not (Test-RunbookHealth)) {
    Write-Host "Service is not UP. Starting Spring Boot in a separate PowerShell window..."

    # Inputs are sent per job. The local adapter only needs an allow-list at process start.
    $dataRoot = To-ForwardSlashPath (Split-Path -Parent $InputDataPath)
    $evidenceRoot = To-ForwardSlashPath (Split-Path -Parent $InputEvidencePath)
    $artifactsArg = To-ForwardSlashPath $ArtifactsRoot

    $startCommand = @"
Set-Location '$RunbookServiceDir'
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--runbook.agent.type=file --runbook.local-input.allowed-roots[0]=$dataRoot --runbook.local-input.allowed-roots[1]=$evidenceRoot --runbook.artifacts-root=$artifactsArg"
"@

    Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", $startCommand | Out-Null

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        if (Test-RunbookHealth) {
            break
        }
        Write-Host "Waiting for service..."
    }

    if (-not (Test-RunbookHealth)) {
        throw "Runbook service did not become UP within $StartupTimeoutSeconds seconds."
    }
}

Write-Host "Health: UP"

Write-Step "Reading sentinel-backend Git commit"

$sha = (& git -C $TargetRepo rev-parse HEAD).Trim()
if (-not $sha) {
    throw "Could not determine Git commit SHA for $TargetRepo"
}
Write-Host "Commit SHA: $sha"

Write-Step "Creating runbook job"

$body = @{
    serviceId = $ServiceId
    repository = @{
        mode = "LOCAL_PATH"
        localPath = $TargetRepo
        commitSha = $sha
    }
    deployment = @{
        environment = "LOCAL"
        applicationVersion = "local-poc"
        imageTag = "${ServiceId}:local-poc"
        buildNumber = "1"
        namespace = "local"
        deploymentName = $ServiceId
    }
    extractionInput = @{
        mode = "PREGENERATED_FILES"
        dataPath = $InputDataPath
        evidencePath = $InputEvidencePath
    }
} | ConvertTo-Json -Depth 5

if ($InputSecurityFindingsPath) {
    if (-not (Test-Path $InputSecurityFindingsPath)) { throw "Security findings path not found: $InputSecurityFindingsPath" }
    $bodyObject = $body | ConvertFrom-Json
    $bodyObject.extractionInput | Add-Member -NotePropertyName securityFindingsPath -NotePropertyValue $InputSecurityFindingsPath
    $body = $bodyObject | ConvertTo-Json -Depth 6
}

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/runbooks/jobs" `
    -ContentType "application/json" `
    -Body $body

$jobId = $response.jobId
if (-not $jobId) {
    throw "Job API did not return a jobId."
}

Write-Host "Job ID: $jobId"
Write-Host "Initial status: $($response.status)"

Write-Step "Waiting for runbook processing to finish"

$terminalStatuses = @(
    "READY_TO_PUBLISH",
    "RENDERED_PUBLISH_BLOCKED",
    "NO_OPERATIONAL_CHANGE",
    "FAILED"
)

$jobDeadline = (Get-Date).AddSeconds($JobTimeoutSeconds)
$lastStatus = $null
$job = $null

while ((Get-Date) -lt $jobDeadline) {
    $job = Invoke-RestMethod -Uri "$BaseUrl/api/v1/runbooks/jobs/$jobId" -Method Get

    if ($job.status -ne $lastStatus) {
        Write-Host "Status: $($job.status)"
        $lastStatus = $job.status
    }

    if ($terminalStatuses -contains $job.status) {
        break
    }

    Start-Sleep -Seconds 2
}

if (-not $job) {
    throw "Could not read job status."
}

if (-not ($terminalStatuses -contains $job.status)) {
    throw "Job did not finish within $JobTimeoutSeconds seconds. Last status: $($job.status)"
}

Write-Step "Final result"

Write-Host "Job ID:              $jobId"
Write-Host "Status:              $($job.status)"
Write-Host "Requested commit:    $($job.requestedCommitSha)"
Write-Host "Analyzed commit:     $($job.actualAnalyzedCommitSha)"
Write-Host "Operational change:  $($job.operationalChange)"
Write-Host "Changed sections:    $($job.changedSections -join ', ')"

if ($job.failureCode) {
    Write-Host "Failure code:         $($job.failureCode)"
}
if ($job.failureMessage) {
    Write-Host "Failure message:      $($job.failureMessage)"
}

$jobRoot = Join-Path (Join-Path $ArtifactsRoot $ServiceId) $jobId
if ($job.artifacts -and $job.artifacts.root) {
    $jobRoot = $job.artifacts.root
}

Write-Host "Artifact root:        $jobRoot"

if ($job.status -eq "FAILED") {
    Write-Host ""
    Write-Host "The job FAILED. No publish call was made."
    if (Test-Path $jobRoot) {
        Start-Process explorer.exe $jobRoot
    }
    exit 1
}

$renderDir = Join-Path $jobRoot "render"
$runbookMd = Join-Path $renderDir "RUNBOOK.md"
$confluenceHtml = Join-Path $renderDir "confluence-body.html"
$generationReport = Join-Path $jobRoot "report\generation-report.json"

Write-Host ""
Write-Host "Generated render artifacts:"
Write-Host "  $runbookMd"
Write-Host "  $confluenceHtml"
Write-Host "  $generationReport"

if (Test-Path $generationReport) {
    $report = Get-Content $generationReport -Raw | ConvertFrom-Json
    Write-Host "Quality gate:        $($report.qualityGate)"
    Write-Host "Publish eligible:    $($report.publishEligible)"
}

Write-Host ""
Write-Host "IMPORTANT: This script intentionally does NOT call /publish."

if (Test-Path $renderDir) {
    Start-Process explorer.exe $renderDir
}
else {
    Write-Warning "Render directory was not found: $renderDir"
}
