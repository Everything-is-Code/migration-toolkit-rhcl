#!/usr/bin/env bash
# Fail when the PR body does not reference a GitHub issue via Closes/Fixes #N.
#
# Accepted (case-insensitive, anywhere in the body):
#   Closes #210
#   Fixes #196
#   Closes part of #210   — epic / stacked PR slices (e.g. #210 coverage tiers)
#   Fixes part of #210
set -euo pipefail

body="${PR_BODY:-}"

if printf '%s' "$body" | grep -qiE '(closes|fixes)(\s+part\s+of)?\s+#[0-9]+'; then
  echo "PR traceability OK: issue reference found."
  exit 0
fi

echo "PR body must include one of:"
echo "  Closes #N"
echo "  Fixes #N"
echo "  Closes part of #N   (stacked PRs under an epic)"
echo "  Fixes part of #N"
echo "Example: Closes #196"
echo "Example: Closes part of #210"
exit 1
