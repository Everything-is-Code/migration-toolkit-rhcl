#!/usr/bin/env bash
# Fail when the PR body does not reference a GitHub issue via Closes/Fixes #N.
set -euo pipefail

body="${PR_BODY:-}"

if printf '%s' "$body" | grep -qiE '(closes|fixes)\s+#[0-9]+'; then
  echo "PR traceability OK: issue reference found."
  exit 0
fi

echo "PR body must include Closes #N or Fixes #N linking to a GitHub issue."
echo "Example: Closes #196"
exit 1
