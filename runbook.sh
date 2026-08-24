#!/usr/bin/env bash
# ==============================================================================
# IDFC AI Runbook Service - Interactive & CLI Run Script
# ==============================================================================
set -euo pipefail

BASE_URL="http://localhost:8080"
SERVICE_ID=""
REPO_MODE="BITBUCKET"
REPO_URL=""
REPO_BRANCH=""
COMMIT_SHA=""
LOCAL_PATH=""
ENVIRONMENT="TEST"
NO_OPEN=false

show_help() {
  cat << EOF
AI Runbook Generator CLI

Usage:
  ./runbook.sh [OPTIONS]

Interactive Mode:
  ./runbook.sh
    (Prompts for Service ID, Repository, and Branch interactively)

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
  -h, --help               Show this help message

Examples:
  ./runbook.sh
  ./runbook.sh --service payments-service --repo https://bitbucket.bank.local/scm/pay/payments.git --branch develop
  ./runbook.sh --mode local --service payments-service --path /Users/user/repos/payments-service
EOF
  exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE_ID="$2"; shift 2 ;;
    -r|--repo)
      REPO_URL="$2"; REPO_MODE="BITBUCKET"; shift 2 ;;
    -b|--branch)
      REPO_BRANCH="$2"; shift 2 ;;
    -c|--commit)
      COMMIT_SHA="$2"; shift 2 ;;
    -m|--mode)
      REPO_MODE="$(echo "$2" | tr '[:lower:]' '[:upper:]')"; shift 2 ;;
    -p|--path)
      LOCAL_PATH="$2"; REPO_MODE="LOCAL_PATH"; shift 2 ;;
    -e|--env)
      ENVIRONMENT="$2"; shift 2 ;;
    -u|--url)
      BASE_URL="$2"; shift 2 ;;
    --no-open)
      NO_OPEN=true; shift ;;
    -h|--help)
      show_help ;;
    *)
      echo "Unknown argument: $1" >&2
      show_help ;;
  esac
done

echo "============================================================"
echo " IDFC AI Production Support Runbook Generator"
echo "============================================================"

# 1. Verify Service Health
echo -n "Checking AI Runbook Service health at $BASE_URL... "
HEALTH_RESPONSE=$(curl -s -m 5 "$BASE_URL/actuator/health" || echo "")
if [[ "$HEALTH_RESPONSE" != *"\"UP\""* && "$HEALTH_RESPONSE" != *"UP"* ]]; then
  echo "UNAVAILABLE"
  echo ""
  echo "[ERROR] AI Runbook Service is not running on $BASE_URL."
  echo "Please start the service in another terminal first:"
  echo ""
  echo "  mvn spring-boot:run -Dspring-boot.run.profiles=local"
  echo ""
  exit 1
fi
echo "UP"

# 2. Interactive Input if required parameters are missing
if [[ -z "$SERVICE_ID" ]]; then
  echo ""
  echo "Select Repository Mode:"
  echo "  1) Bitbucket remote repository (Default)"
  echo "  2) Local directory checkout"
  read -r -p "Choice [1]: " MODE_CHOICE
  MODE_CHOICE="${MODE_CHOICE:-1}"

  if [[ "$MODE_CHOICE" == "2" ]]; then
    REPO_MODE="LOCAL_PATH"
  else
    REPO_MODE="BITBUCKET"
  fi

  echo ""
  while [[ -z "$SERVICE_ID" ]]; do
    read -r -p "Service ID (e.g. payments-service): " SERVICE_ID
  done

  if [[ "$REPO_MODE" == "BITBUCKET" ]]; then
    while [[ -z "$REPO_URL" ]]; do
      read -r -p "Bitbucket repository URL: " REPO_URL
    done
    read -r -p "Branch (optional, press Enter for remote HEAD): " REPO_BRANCH
  else
    while [[ -z "$LOCAL_PATH" ]]; do
      read -r -p "Local repository path: " LOCAL_PATH
    done
  fi
fi

echo ""
echo "Configuration:"
echo "  Service ID:  $SERVICE_ID"
echo "  Mode:        $REPO_MODE"
if [[ "$REPO_MODE" == "BITBUCKET" ]]; then
  echo "  Repository:  $REPO_URL"
  echo "  Branch:      ${REPO_BRANCH:-[Default / Remote HEAD]}"
  if [[ -n "$COMMIT_SHA" ]]; then
    echo "  Commit SHA:  $COMMIT_SHA (exact)"
  fi
else
  echo "  Local Path:  $LOCAL_PATH"
  if [[ -n "$COMMIT_SHA" ]]; then
    echo "  Commit SHA:  $COMMIT_SHA (exact)"
  fi
fi
echo "  Environment: $ENVIRONMENT"
echo ""

# 3. Build JSON Request Payload
PAYLOAD=$(python3 -c "
import json
data = {
    'serviceId': '$SERVICE_ID',
    'repository': {
        'mode': '$REPO_MODE'
    },
    'deployment': {
        'environment': '$ENVIRONMENT'
    }
}
if '$REPO_MODE' == 'BITBUCKET':
    data['repository']['url'] = '$REPO_URL'
    if '$REPO_BRANCH':
        data['repository']['branch'] = '$REPO_BRANCH'
    if '$COMMIT_SHA':
        data['repository']['commitSha'] = '$COMMIT_SHA'
else:
    data['repository']['localPath'] = '$LOCAL_PATH'
    if '$COMMIT_SHA':
        data['repository']['commitSha'] = '$COMMIT_SHA'

print(json.dumps(data))
" 2>/dev/null || cat << EOF
{
  "serviceId": "$SERVICE_ID",
  "repository": {
    "mode": "$REPO_MODE",
    $([ "$REPO_MODE" = "BITBUCKET" ] && echo "\"url\": \"$REPO_URL\", $([ -n "$REPO_BRANCH" ] && echo "\"branch\": \"$REPO_BRANCH\",)")
    $([ "$REPO_MODE" = "LOCAL_PATH" ] && echo "\"localPath\": \"$LOCAL_PATH\",")
    $([ -n "$COMMIT_SHA" ] && echo "\"commitSha\": \"$COMMIT_SHA\",")
    "dummy": ""
  },
  "deployment": {
    "environment": "$ENVIRONMENT"
  }
}
EOF
)

# Clean up payload formatting if fallback was used
PAYLOAD=$(echo "$PAYLOAD" | sed 's/, "dummy": ""//g' | sed 's/,"dummy":""//g')

# 4. Submit Runbook Job
echo "Submitting runbook generation job..."
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/runbooks/jobs" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

JOB_ID=$(python3 -c "import json, sys; print(json.loads('''$CREATE_RESPONSE''').get('jobId', ''))" 2>/dev/null || \
  echo "$CREATE_RESPONSE" | grep -o '"jobId":"[^"]*' | cut -d'"' -f4)

if [[ -z "$JOB_ID" ]]; then
  echo "[ERROR] Failed to submit job. Response from server:"
  echo "$CREATE_RESPONSE"
  exit 1
fi

echo "Job created successfully: $JOB_ID"
echo ""
echo "Processing runbook pipeline..."

# 5. Poll Job Status
LAST_STATUS=""
TERMINAL_STATUSES=("READY_TO_PUBLISH" "NO_OPERATIONAL_CHANGE" "RENDERED_PUBLISH_BLOCKED" "FAILED")

while true; do
  JOB_JSON=$(curl -s "$BASE_URL/api/v1/runbooks/jobs/$JOB_ID")
  
  CURRENT_STATUS=$(python3 -c "import json; print(json.loads('''$JOB_JSON''').get('status', 'UNKNOWN'))" 2>/dev/null || \
    echo "$JOB_JSON" | grep -o '"status":"[^"]*' | cut -d'"' -f4)

  if [[ "$CURRENT_STATUS" != "$LAST_STATUS" ]]; then
    echo "  ➜ Status: $CURRENT_STATUS"
    LAST_STATUS="$CURRENT_STATUS"
  fi

  for TERM in "${TERMINAL_STATUSES[@]}"; do
    if [[ "$CURRENT_STATUS" == "$TERM" ]]; then
      break 2
    fi
  done

  sleep 2
done

echo ""

# 6. Parse and Print Results
python3 -c "
import json, os, sys

job = json.loads('''$JOB_JSON''')
status = job.get('status', 'UNKNOWN')
service_id = job.get('serviceId', '$SERVICE_ID')
req_commit = job.get('requestedCommitSha', '')
ana_commit = job.get('actualAnalyzedCommitSha', '')
change = job.get('operationalChange', False)
changed_sections = ', '.join(job.get('changedSections', []))
artifacts = job.get('artifacts', {})
root = artifacts.get('root', '')
failure_code = job.get('failureCode', '')
failure_msg = job.get('failureMessage', '')

if status in ('READY_TO_PUBLISH', 'NO_OPERATIONAL_CHANGE', 'RENDERED_PUBLISH_BLOCKED'):
    print('============================================================')
    print(' RUNBOOK GENERATION COMPLETED')
    print('============================================================')
    print(f'Service ID:             {service_id}')
    print(f'Repository:             $REPO_URL' if '$REPO_MODE' == 'BITBUCKET' else f'Local Path:             $LOCAL_PATH')
    if '$REPO_BRANCH':
        print(f'Branch:                 $REPO_BRANCH')
    print(f'Requested Commit:       {req_commit}')
    print(f'Analyzed Commit:        {ana_commit}')
    print(f'Job ID:                 {job.get(\"jobId\", \"\")}')
    print(f'Final Status:           {status}')
    print(f'Operational Change:     {change}')
    if changed_sections:
        print(f'Changed Sections:       {changed_sections}')
    print(f'Artifact Root:          {root}')
    print('')
    print('Generated Artifacts:')
    print(f'  Markdown Runbook:     {os.path.join(root, \"render\", \"RUNBOOK.md\")}')
    print(f'  Confluence HTML:      {os.path.join(root, \"render\", \"confluence-body.html\")}')
    print(f'  Generation Report:    {os.path.join(root, \"report\", \"generation-report.json\")}')
    print('============================================================')
else:
    print('============================================================')
    print(' RUNBOOK GENERATION FAILED')
    print('============================================================')
    print(f'Service ID:             {service_id}')
    print(f'Job ID:                 {job.get(\"jobId\", \"\")}')
    print(f'Failure Code:           {failure_code}')
    print(f'Failure Message:        {failure_msg}')
    if root:
        print(f'Artifact Root:          {root}')
        print('')
        print('Diagnostic Logs:')
        print(f'  Stdout log:           {os.path.join(root, \"extraction\", \"idfc-coder.stdout.log\")}')
        print(f'  Stderr log:           {os.path.join(root, \"extraction\", \"idfc-coder.stderr.log\")}')
        print(f'  Validation Report:    {os.path.join(root, \"validation\", \"validation-report.json\")}')
    print('============================================================')
" 2>/dev/null || cat << EOF
============================================================
Final Status: $LAST_STATUS
Job ID:       $JOB_ID
============================================================
EOF

# 7. Prompt to open Confluence HTML if on macOS/Linux/Windows and interactive
ROOT_DIR=$(python3 -c "import json; print(json.loads('''$JOB_JSON''').get('artifacts', {}).get('root', ''))" 2>/dev/null || echo "")
HTML_FILE="$ROOT_DIR/render/confluence-body.html"

if [[ "$LAST_STATUS" == "READY_TO_PUBLISH" || "$LAST_STATUS" == "NO_OPERATIONAL_CHANGE" ]]; then
  if [[ "$NO_OPEN" == false && -f "$HTML_FILE" ]]; then
    echo ""
    read -r -p "Open generated Confluence HTML in browser? [Y/n]: " OPEN_CHOICE
    OPEN_CHOICE="${OPEN_CHOICE:-Y}"
    if [[ "$OPEN_CHOICE" =~ ^[Yy]$ ]]; then
      if command -v open >/dev/null 2>&1; then
        open "$HTML_FILE"
      elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$HTML_FILE"
      elif command -v start >/dev/null 2>&1; then
        start "$HTML_FILE"
      else
        echo "HTML file available at: $HTML_FILE"
      fi
    fi
  fi
fi

if [[ "$LAST_STATUS" == "FAILED" ]]; then
  exit 1
fi
