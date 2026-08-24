# AI Runbook Service - Local Run Guide

A self-contained Spring Boot service that deterministically validates, normalizes, diffs, and renders Production Support runbooks and operational catalogs from AI-extracted Service Intelligence facts.

---

## 1. Quick Start for IDFC Developers (One-Command Workflow)

Local runbook generation requires only two terminal windows:

### Terminal 1: Start the AI Runbook Service
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Wait until you see the Spring Boot startup message on port `8080`.

### Terminal 2: Run the Interactive Script
```bash
./runbook.sh
```

You will be prompted with simple interactive questions:
```text
============================================================
 IDFC AI Production Support Runbook Generator
============================================================
Checking AI Runbook Service health at http://localhost:8080... UP

Select Repository Mode:
  1) Bitbucket remote repository (Default)
  2) Local directory checkout
Choice [1]: 1

Service ID (e.g. payments-service): payments-integration-services
Bitbucket repository URL: https://bitbucket.bank.local/scm/pay/payments.git
Branch (optional, press Enter for remote HEAD): develop
```

The script automatically:
1. Submits the job to the Spring Boot service.
2. Polls pipeline status transitions in real-time (`PREPARING_WORKSPACE` ➜ `EXTRACTING` ➜ `VALIDATING` ➜ `NORMALIZING` ➜ `DIFFING` ➜ `RENDERING` ➜ `READY_TO_PUBLISH`).
3. Reports generated file paths for `RUNBOOK.md`, `confluence-body.html`, and `generation-report.json`.
4. Prompts to open the generated Confluence HTML in your browser.

> **Note:** Developers do NOT need to write raw curl requests or copy job IDs manually.

---

## 2. Prerequisites

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

## 3. Non-Interactive CLI Usage (`./runbook.sh`)

For scripting, automation, or direct CLI execution:

### Bitbucket Remote Repository (Recommended)
```bash
./runbook.sh \
  --service payments-integration-services \
  --repo https://bitbucket.bank.local/scm/pay/payments.git \
  --branch develop
```

### Exact Commit Analysis (CI/CD / Production)
```bash
./runbook.sh \
  --service payments-integration-services \
  --repo https://bitbucket.bank.local/scm/pay/payments.git \
  --commit 6ed4594439c50e6943e5dff52fc53ac41dbc68c5
```

### Local Directory Mode
```bash
./runbook.sh \
  --mode local \
  --service payments-service \
  --path /Users/username/repos/payments-service
```

### CLI Options Summary
```text
Options:
  -s, --service <id>       Service ID (e.g. payments-integration-services)
  -r, --repo <url>         Bitbucket / Git repository URL
  -b, --branch <branch>    Branch name (optional; defaults to remote HEAD)
  -c, --commit <sha>       Exact commit SHA (optional; overrides remote HEAD)
  -m, --mode <mode>        Repository mode: BITBUCKET (default) or LOCAL_PATH
  -p, --path <path>        Local repository path (when mode is LOCAL_PATH)
  -e, --env <environment>  Deployment environment: TEST (default), LOCAL, PRODUCTION
  -u, --url <base-url>     AI Runbook Service URL (default: http://localhost:8080)
      --no-open            Do not prompt to open generated Confluence HTML
  -h, --help               Show help message
```

---

## 4. Execution Modes & Server Startup

### Mode A: Local `idfc-coder` Agent (Default)
Executes `idfc-coder` to perform AI extraction from the target repository:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Mode B: Pre-generated Extraction Files (Offline / Test Mode)
Loads pre-existing `runbook-data.json` and `runbook-evidence.json` without invoking the CLI agent:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--runbook.agent.type=file --runbook.local-input.allowed-roots[0]=/path/to/runbook-test-output"
```

### Run Packaged JAR
```bash
mvn clean package -DskipTests
java -jar target/ai-runbook-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

### Verify Server Health
```bash
curl http://localhost:8080/actuator/health
# Response: {"status":"UP"}
```

---

## 5. Repository Analysis Modes

### 1. BITBUCKET Mode (Default for IDFC Testing)
- **Local/Manual testing**: Pass repository URL and optional branch. The service automatically resolves the current remote HEAD SHA without requiring a local git checkout.
- **CI/CD / Production**: Supply an exact `commitSha`. The service analyzes that exact commit and verifies it against the extracted commit.

### 2. LOCAL_PATH Mode
- Analyzes a local checked-out git repository on the developer's filesystem.

---

## 6. Where Generated Files Are Created

Artifacts are written under:
`build/runbook-artifacts/<serviceId>/<jobId>/`

```text
build/runbook-artifacts/payments-service/<jobId>/
├── extraction/
│   ├── runbook-data.json            # AI-extracted raw facts
│   ├── runbook-evidence.json        # Source code line-level citations
│   ├── security-findings.json       # Security findings (restricted)
│   ├── idfc-coder.stdout.log        # Extraction agent stdout
│   └── idfc-coder.stderr.log        # Extraction agent stderr
├── validation/
│   └── validation-report.json       # Schema, safety, and evidence validation report
├── normalized/
│   └── normalized-runbook-data.json # Canonicalized, sorted, deduplicated model
├── diff/
│   ├── operational-diff.json        # Semantic diff vs latest baseline
│   └── runbook-delta.json           # Delta artifact
├── render/
│   ├── RUNBOOK.md                   # 23-section Production Support Runbook
│   ├── confluence-body.html         # Confluence-ready XHTML storage format
│   ├── configuration-catalog.md     # Dedicated Configuration Catalog
│   ├── api-catalog.md               # Dedicated API Catalog
│   ├── business-rules-catalog.md    # Dedicated Business Rules Catalog
│   ├── observability-catalog.md     # Logs, metrics, health, trace catalog
│   ├── architecture-document.md     # Architecture summary
│   └── release-impact.md            # Release operational impact
└── report/
    └── generation-report.json       # Audit report with prompt fingerprint & quality gate
```

---

## 7. Advanced / Manual API Usage

For developers integrating directly with the REST API:

### Create Runbook Job (BITBUCKET Mode)
```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "payments-service",
    "repository": {
      "mode": "BITBUCKET",
      "url": "https://bitbucket.bank.local/scm/pay/payments.git",
      "branch": "develop"
    },
    "deployment": {
      "environment": "TEST"
    }
  }'
```

### Create Runbook Job (LOCAL_PATH Mode)
```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "payments-service",
    "repository": {
      "mode": "LOCAL_PATH",
      "localPath": "/Users/username/repos/payments-service"
    },
    "deployment": {
      "environment": "TEST"
    }
  }'
```

### Check Job Status
```bash
curl http://localhost:8080/api/v1/runbooks/jobs/<JOB_UUID>
```

### Publish to Confluence (Deployment Pipeline)
```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs/<JOB_UUID>/publish \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "TEST",
    "deployedCommitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5",
    "deployedImageTag": "payments-service:1.0.0"
  }'
```

---

## 8. Troubleshooting & Error Codes

| Error Code / Message | Root Cause | How to Fix |
|---|---|---|
| `RUNBOOK_AGENT_FAILED` | `idfc-coder` binary failed or returned non-zero exit code. | Inspect `extraction/idfc-coder.stderr.log` in the artifact folder. Verify `idfc-coder` is on PATH. |
| `RUNBOOK_AGENT_TIMEOUT` | AI extraction exceeded execution timeout. | Increase timeout in `application.yml` via `runbook.agent.timeout` (default is `30m`). |
| `RUNBOOK_EXTRACTION_MISSING` | Extraction finished but `runbook-data.json` or `runbook-evidence.json` was not created. | Verify `idfc-coder` write permissions in the artifact output directory. |
| `RUNBOOK_FILE_AGENT_INVALID` | `dataPath` or `evidencePath` is outside allowed roots. | Add the parent directory of your test files to `runbook.local-input.allowed-roots` in `application-local.yml`. |
| `RUNBOOK_BRANCH_NOT_FOUND` | Specified branch does not exist on remote repository. | Verify branch name on Bitbucket. |
| `RUNBOOK_COMMIT_MISMATCH` | Extracted commit does not match requested/resolved commit. | Ensure extraction agent analyzes the specified commit SHA. |
| `RUNBOOK_SCHEMA_INVALID` | Generated JSON failed contract validation. | Inspect `validation/validation-report.json` for specific field errors. |
| `SAFETY_POLICY_VIOLATION` | Extraction generated unsafe Production Support instructions (e.g. database updates, event replays). | Ensure extraction prompt v3 is used and support guidance is strictly read-only. |
| `RUNBOOK_DEPLOYED_COMMIT_MISMATCH` | Publish request commit SHA does not match analyzed commit. | Pass the exact analyzed commit SHA in `deployedCommitSha`. |

---

## 9. Feature Support Status

| Feature | Status | Notes |
|---|---|---|
| Bundled 2.1 Specification (`runbook-spec`) | **IMPLEMENTED** | Bundled on classpath; zero external dependencies. |
| One-Command Interactive Run (`runbook.sh`) | **IMPLEMENTED** | Interactive & CLI flags on macOS/Linux. |
| Bitbucket Remote HEAD Resolution | **IMPLEMENTED** | Uses `git ls-remote` for automatic branch resolution. |
| Local Directory Analysis (`LOCAL_PATH`) | **IMPLEMENTED** | Supported via CLI & API. |
| Pre-generated File Extraction Mode | **IMPLEMENTED** | Local profile only (`runbook.agent.type=file`). |
| Safety & Read-Only Policy Validation | **IMPLEMENTED** | Rejects affirmative mutation instructions. |
| 23-Section Support Markdown & HTML Rendering | **IMPLEMENTED** | Frozen headings, deterministic formatting. |
| Semantic Diffing & Baseline Comparison | **IMPLEMENTED** | In-memory & local filesystem baseline store. |
| Direct Confluence REST API Client | **NOT YET IMPLEMENTED** | Publisher interface is ready; currently uses local mock registry. |
| Persistent Database Job Store | **NOT YET IMPLEMENTED** | Uses thread-safe in-memory store (`InMemoryRunbookJobStore`). |
