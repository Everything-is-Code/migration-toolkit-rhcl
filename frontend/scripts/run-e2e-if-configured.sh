#!/usr/bin/env bash
# Run Playwright E2E when lab credentials are available.
# Used by /verify when frontend/ changed.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${THREESCALE_ADMIN_URL:-}" || -z "${THREESCALE_ACCESS_TOKEN:-}" ]]; then
  echo "SKIP: THREESCALE_ADMIN_URL / THREESCALE_ACCESS_TOKEN not set — UI E2E skipped"
  exit 2
fi

export E2E_SKIP_WEBSERVER="${E2E_SKIP_WEBSERVER:-true}"
echo "Running Playwright E2E (YAML verification)..."
npm run test:e2e
