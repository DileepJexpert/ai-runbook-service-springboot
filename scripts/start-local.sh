#!/usr/bin/env bash
# ==============================================================================
# IDFC AI Runbook Service - Local Startup Helper
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ -f "$SCRIPT_DIR/local-env.sh" ]]; then
  echo "Sourcing local environment from scripts/local-env.sh..."
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/local-env.sh"
else
  echo "============================================================"
  echo "[WARNING] Missing scripts/local-env.sh"
  echo "============================================================"
  echo "Please create scripts/local-env.sh by copying the template:"
  echo ""
  echo "  cp scripts/local-env.example.sh scripts/local-env.sh"
  echo ""
  echo "Then edit scripts/local-env.sh with your corporate AI credentials."
  echo "============================================================"
fi

echo "Starting AI Runbook Service (profile: local)..."
cd "$ROOT_DIR"
mvn spring-boot:run -Dspring-boot.run.profiles=local
