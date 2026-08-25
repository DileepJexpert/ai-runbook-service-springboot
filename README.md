# AI Runbook Service - Local Run Guide

A self-contained Spring Boot service that generates authoritative, 23-section Production Support Runbooks for IDFC microservices using a fast, deterministic LEAN generation pipeline connected to the corporate LLM.

```text
Repository (Bitbucket / Local Path)
  ↓
Java Context Collection (RepositoryContextCollector)
  ↓
Corporate Auth & Direct LLM API (RunbookAiClient)
  ↓
Markdown Runbook (render/RUNBOOK.md)
  ↓
Lightweight Validation (23 sections, safety, secrets)
  ↓
Confluence HTML (render/confluence-body.html)
  ↓
READY_TO_PUBLISH
```

---

## LOCAL IDFC LAPTOP SETUP

### Step 1: Clone Repository
```bash
git clone <repo-url>
cd ai-runbook-service-springboot
```

### Step 2: Create Local Environment Configuration
```bash
cp scripts/local-env.example.sh scripts/local-env.sh
```

### Step 3: Edit `scripts/local-env.sh`
Edit `scripts/local-env.sh` and set your corporate AI credentials:
```bash
export RUNBOOK_AI_BASE_URL="https://<corporate-ai-base-url>/v1/chat/completions"
export RUNBOOK_AI_AUTH_URL="https://<corporate-auth-url>/oauth/token"
export RUNBOOK_AI_MODEL="gpt-4o"
export RUNBOOK_AI_USERNAME="your-username"
export RUNBOOK_AI_PASSWORD="your-password"
```

### Step 4: Source Environment Variables
```bash
source scripts/local-env.sh
```

### Step 5: Start AI Runbook Service (Terminal 1)
```bash
./scripts/start-local.sh
```
*(Or run `mvn spring-boot:run -Dspring-boot.run.profiles=local`)*

### Step 6: Generate Runbook (Terminal 2)
```bash
./runbook.sh
```
Follow the interactive prompts:
1. Select Repository Mode (`1` for Bitbucket, `2` for Local Path).
2. Enter Bitbucket Repository URL (e.g. `https://bitbucket.bank.local/scm/pay/payments-service.git`).
3. Press Enter for default branch / auto-derived Service ID.

### Expected Result:
Generated runbook files are written to:
- Markdown Runbook: `build/runbook-artifacts/<serviceId>/<jobId>/render/RUNBOOK.md`
- Confluence HTML: `build/runbook-artifacts/<serviceId>/<jobId>/render/confluence-body.html`

---

## CLI Options Summary (`./runbook.sh`)

For scripting, automation, or non-interactive CLI execution:

```bash
# Bitbucket remote repository (Recommended)
./runbook.sh \
  --repo https://bitbucket.bank.local/scm/pay/payments-service.git \
  --branch develop

# Exact commit analysis
./runbook.sh \
  --repo https://bitbucket.bank.local/scm/pay/payments-service.git \
  --commit 6ed4594439c50e6943e5dff52fc53ac41dbc68c5

# Local checkout mode
./runbook.sh \
  --mode local \
  --path /Users/username/repos/payments-service
```

```text
Options:
  -s, --service <id>       Service ID (optional; auto-derived from repo URL/path if omitted)
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
