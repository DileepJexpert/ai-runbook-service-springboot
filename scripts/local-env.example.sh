#!/usr/bin/env bash
# ==============================================================================
# IDFC AI Runbook Service - Local Environment Configuration Template
# ==============================================================================
# Copy this file locally to:
#   cp scripts/local-env.example.sh scripts/local-env.sh
#
# Then edit scripts/local-env.sh with your corporate AI credentials.
# scripts/local-env.sh is gitignored and will never be committed.
# ==============================================================================

export RUNBOOK_AI_BASE_URL="<corporate-ai-base-url>"
export RUNBOOK_AI_AUTH_URL="<corporate-auth-url>"
export RUNBOOK_AI_MODEL="<model-name>"
export RUNBOOK_AI_USERNAME="<your-username>"
export RUNBOOK_AI_PASSWORD="<your-password>"

export RUNBOOK_AI_CONNECT_TIMEOUT_SECONDS="300"
export RUNBOOK_AI_REQUEST_TIMEOUT_SECONDS="900"
export RUNBOOK_AI_MAX_TOKENS="12000"
