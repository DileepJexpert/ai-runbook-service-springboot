# AI Runbook Service - Local Run Guide

A self-contained Spring Boot service that deterministically validates, normalizes, diffs, and renders Production Support runbooks and operational catalogs from AI-extracted Service Intelligence facts.

---

## 1. Prerequisites

Ensure the following tools are installed and available on your PATH:

- **Java**: Java 21 LTS or newer
- **Maven**: Apache Maven 3.9+
- **Git**: Git 2.30+
- **idfc-coder**: Local CLI extraction binary (*required only when using default `runbook.agent.type=local`*)

### Verify Environment

**macOS / Linux:**
```bash
java -version
mvn -version
git --version
idfc-coder --version
```

**Windows (PowerShell):**
```powershell
java -version
mvn -version
git --version
idfc-coder --version
```

---

## 2. How to Start the Service

The service can be run in two execution modes:

### Mode A: Local `idfc-coder` Agent (Default)
Executes the `idfc-coder` CLI tool directly on the target repository to perform live AI fact extraction.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Mode B: Pre-generated Extraction Files (POC / Offline Mode)
Loads existing `runbook-data.json` and `runbook-evidence.json` files without running the extraction CLI.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--runbook.agent.type=file --runbook.local-input.allowed-roots[0]=/path/to/runbook-test-output"
```

*On Windows PowerShell:*
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local" `
  "-Dspring-boot.run.arguments=--runbook.agent.type=file --runbook.local-input.allowed-roots[0]=C:/path/to/runbook-test-output"
```

### Or Run the Packaged JAR
```bash
mvn clean package -DskipTests
java -jar target/ai-runbook-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

### Verify Service Health
Check that the application has started on port `8080`:

```bash
curl http://localhost:8080/actuator/health
```
**Expected Response:**
```json
{"status":"UP"}
```

---

## 3. How to Analyze One Service

Submit an analysis job via `POST /api/v1/runbooks/jobs`.

### Option 1: Live Extraction with `idfc-coder` (Mode A)

**cURL (macOS / Linux):**
```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "payments-service",
    "repository": {
      "mode": "LOCAL_PATH",
      "localPath": "/Users/username/repos/payments-service",
      "commitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5"
    },
    "deployment": {
      "environment": "TEST",
      "applicationVersion": "1.0.0",
      "imageTag": "payments-service:1.0.0",
      "buildNumber": "101",
      "namespace": "payments",
      "deploymentName": "payments-service"
    }
  }'
```

**PowerShell (Windows):**
```powershell
$body = @{
    serviceId = "payments-service"
    repository = @{
        mode = "LOCAL_PATH"
        localPath = "C:/repos/payments-service"
        commitSha = "6ed4594439c50e6943e5dff52fc53ac41dbc68c5"
    }
    deployment = @{
        environment = "TEST"
        applicationVersion = "1.0.0"
        imageTag = "payments-service:1.0.0"
        buildNumber = "101"
        namespace = "payments"
        deploymentName = "payments-service"
    }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/runbooks/jobs" -Method Post -ContentType "application/json" -Body $body
```

### Option 2: Pre-generated Extraction Files (Mode B)

**cURL (macOS / Linux):**
```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "payments-service",
    "repository": {
      "mode": "LOCAL_PATH",
      "localPath": "/Users/username/repos/payments-service",
      "commitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5"
    },
    "deployment": {
      "environment": "LOCAL",
      "applicationVersion": "local-poc",
      "imageTag": "payments-service:local-poc"
    },
    "extractionInput": {
      "mode": "PREGENERATED_FILES",
      "dataPath": "/path/to/runbook-test-output/runbook-data.json",
      "evidencePath": "/path/to/runbook-test-output/runbook-evidence.json",
      "securityFindingsPath": "/path/to/runbook-test-output/security-findings.json"
    }
  }'
```

---

## 4. How to Analyze Multiple Services

You can submit multiple jobs concurrently to the service. Each job:
- Receives a unique `jobId` (UUID).
- Processes asynchronously in the background thread pool (`runbook.executor.max-pool-size`).
- Generates artifacts in an isolated directory: `build/runbook-artifacts/<serviceId>/<jobId>/`.

**Example:**
```bash
# Service 1
curl -X POST http://localhost:8080/api/v1/runbooks/jobs -H "Content-Type: application/json" \
  -d '{"serviceId":"cbs-adapter","repository":{"mode":"LOCAL_PATH","localPath":"/repos/cbs-adapter"},"deployment":{"environment":"TEST"}}'

# Service 2
curl -X POST http://localhost:8080/api/v1/runbooks/jobs -H "Content-Type: application/json" \
  -d '{"serviceId":"rail-gateway","repository":{"mode":"LOCAL_PATH","localPath":"/repos/rail-gateway"},"deployment":{"environment":"TEST"}}'
```

---

## 5. How to Check Job Status

Poll the job endpoint using the `jobId` returned from the creation request:

```bash
curl http://localhost:8080/api/v1/runbooks/jobs/<JOB_UUID>
```

**Example Response:**
```json
{
  "jobId": "f7d98341-3b7c-4731-a8cf-824c8702fb71",
  "serviceId": "payments-service",
  "status": "READY_TO_PUBLISH",
  "requestedCommitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5",
  "actualAnalyzedCommitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5",
  "operationalChange": true,
  "changedSections": ["APIs", "Database Tables", "Downstream Dependencies"],
  "artifacts": {
    "root": "build/runbook-artifacts/payments-service/f7d98341-3b7c-4731-a8cf-824c8702fb71"
  },
  "failureCode": "",
  "failureMessage": ""
}
```

### Lifecycle States:
| State | Meaning |
|---|---|
| `SUBMITTED` | Job received and queued. |
| `PREPARING_WORKSPACE` | Resolving local repository and commit SHA. |
| `EXTRACTING` | AI extraction running via `idfc-coder` or reading pre-generated files. |
| `VALIDATING` | Deterministic schema, safety policy, and evidence validation. |
| `NORMALIZING` | Canonicalizing facts and calculating stable IDs. |
| `DIFFING` | Semantic comparison against previous successful baseline. |
| `RENDERING` | Generating Markdown and Confluence HTML documents. |
| `READY_TO_PUBLISH` | Quality gates passed; ready for deployment and publishing. |
| `NO_OPERATIONAL_CHANGE` | No semantic diff detected; publishing skipped. |
| `RENDERED_PUBLISH_BLOCKED` | Quality gate failed (e.g. partial scan on production). |
| `PUBLISHING` | Updating Confluence page. |
| `PUBLISHED` | Published successfully and baseline saved. |
| `FAILED` | Job encountered an error (see `failureCode` / `failureMessage`). |

---

## 6. Where Generated Files Are Created

Artifacts are written under:
`build/runbook-artifacts/<serviceId>/<jobId>/`

```text
build/runbook-artifacts/payments-service/<jobId>/
├── extraction/
│   ├── runbook-data.json            # AI extracted raw facts
│   ├── runbook-evidence.json        # Line-level source code citations
│   ├── security-findings.json       # Restricted security review candidates (optional)
│   ├── idfc-coder.stdout.log        # Extraction agent stdout
│   └── idfc-coder.stderr.log        # Extraction agent stderr
├── validation/
│   └── validation-report.json       # Schema, safety, and evidence check results
├── normalized/
│   └── normalized-runbook-data.json # Canonicalized, sorted, deduplicated data
├── diff/
│   ├── operational-diff.json        # Semantic changes vs previous baseline
│   └── runbook-delta.json           # Delta artifact
├── render/
│   ├── RUNBOOK.md                   # Complete 23-section Production Support Runbook
│   ├── confluence-body.html         # Confluence-ready XHTML storage format
│   ├── configuration-catalog.md     # Dedicated Configuration Catalog
│   ├── api-catalog.md               # Dedicated API Catalog
│   ├── business-rules-catalog.md    # Dedicated Business Rules Catalog
│   ├── observability-catalog.md     # Logs, metrics, health, and trace catalog
│   ├── architecture-document.md     # Service architecture overview
│   └── release-impact.md            # Release operational impact summary
└── report/
    └── generation-report.json       # Complete audit report with fingerprint & quality gate
```

---

## 7. How to View Generated Runbooks

Open the rendered documents in your preferred editor or browser:

**macOS:**
```bash
# Open rendered Markdown
open build/runbook-artifacts/payments-service/<jobId>/render/RUNBOOK.md

# Open rendered Confluence HTML in browser
open build/runbook-artifacts/payments-service/<jobId>/render/confluence-body.html
```

**Linux:**
```bash
xdg-open build/runbook-artifacts/payments-service/<jobId>/render/RUNBOOK.md
```

**Windows (PowerShell):**
```powershell
# Open artifact directory in File Explorer
explorer.exe "build\runbook-artifacts\payments-service\<jobId>\render"

# Or open in VS Code
code "build\runbook-artifacts\payments-service\<jobId>\render\RUNBOOK.md"
```

---

## 8. How to Publish to Confluence

Publishing is decoupled from generation and is intended to be called by your CI/CD deployment pipeline after a successful deployment.

### Publishing Request

```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs/<JOB_UUID>/publish \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "TEST",
    "deployedCommitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5",
    "deployedImageTag": "payments-service:1.0.0"
  }'
```

### Publication Safety Requirements:
1. Job status must be `READY_TO_PUBLISH`.
2. `deployedCommitSha` must match the commit analyzed by the job.
3. `mode` must be either `TEST` or `PRODUCTION`.
4. Target page IDs must be configured in `application.yml` under `runbook.targets.<serviceId>`.

> **Note on Confluence Adapter:**
> Direct live REST publishing to remote Confluence Cloud/Server is currently a configurable adapter target (`runbook.confluence.enabled=false` locally). Production Confluence client integration: **NOT YET IMPLEMENTED**.

---

## 9. Troubleshooting & Common Errors

| Error Code / Message | Root Cause | How to Fix |
|---|---|---|
| `RUNBOOK_AGENT_FAILED` | `idfc-coder` binary failed or returned non-zero exit code. | Inspect `extraction/idfc-coder.stderr.log` in the artifact folder. Verify `idfc-coder` is on PATH. |
| `RUNBOOK_AGENT_TIMEOUT` | AI extraction exceeded execution timeout. | Increase timeout in `application.yml` via `runbook.agent.timeout` (default is `30m`). |
| `RUNBOOK_EXTRACTION_MISSING` | Extraction finished but `runbook-data.json` or `runbook-evidence.json` was not created. | Verify `idfc-coder` write permissions in the artifact output directory. |
| `RUNBOOK_FILE_AGENT_INVALID` | `dataPath` or `evidencePath` is outside allowed roots. | Add the parent directory of your test files to `runbook.local-input.allowed-roots` in `application-local.yml`. |
| `RUNBOOK_SCHEMA_INVALID` | Generated JSON failed contract validation. | Inspect `validation/validation-report.json` for specific field errors. |
| `SAFETY_POLICY_VIOLATION` | Extraction generated unsafe Production Support instructions (e.g. database updates, event replays). | Ensure extraction prompt v3 is used and support guidance is strictly read-only. |
| `RUNBOOK_DEPLOYED_COMMIT_MISMATCH` | Publish request commit SHA does not match analyzed commit. | Pass the exact analyzed commit SHA in `deployedCommitSha`. |
| `RUNBOOK_REPOSITORY_INVALID` | `repository.mode` is not `LOCAL_PATH`. | Remote Git clone mode is **NOT YET IMPLEMENTED**. Use `LOCAL_PATH` pointing to a local cloned repository. |
| `RUNBOOK_JOB_NOT_FOUND` | Job UUID does not exist in memory. | Check the `jobId` format. In-memory store resets on service restart. |

---

## 10. Feature Support Status

| Feature | Status | Notes |
|---|---|---|
| Bundled 2.1 Specification (`runbook-spec`) | **IMPLEMENTED** | Bundled on classpath; zero external dependencies. |
| Local CLI Agent Execution (`idfc-coder`) | **IMPLEMENTED** | Default mode. |
| Pre-generated File Extraction Mode | **IMPLEMENTED** | Local profile only (`runbook.agent.type=file`). |
| Multi-Module Monorepo Scoping | **IMPLEMENTED** | Scoped `CONFIG:<COMPONENT_ID>:<KEY>`. |
| Safety & Read-Only Policy Validation | **IMPLEMENTED** | Rejects affirmative mutation instructions. |
| 23-Section Support Markdown & HTML Rendering | **IMPLEMENTED** | Frozen headings, deterministic formatting. |
| Semantic Diffing & Baseline Comparison | **IMPLEMENTED** | In-memory & local filesystem baseline store. |
| Quality Gates (`READY_TO_PUBLISH`) | **IMPLEMENTED** | Complete/partial scan validation. |
| Remote Git URL Auto-Cloning | **NOT YET IMPLEMENTED** | Currently requires local repository clone (`LOCAL_PATH`). |
| Direct Confluence REST API Client | **NOT YET IMPLEMENTED** | Publisher interface is ready; currently uses local mock registry. |
| Persistent Database Job Store | **NOT YET IMPLEMENTED** | Uses thread-safe in-memory store (`InMemoryRunbookJobStore`). |
