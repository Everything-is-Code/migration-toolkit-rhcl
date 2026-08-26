#!/usr/bin/env bash
# Smoke tests for check_pr_traceability.sh (run from repo root).
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check_pr_traceability.sh"

pass() {
  PR_BODY="$1"
  if bash "$script"; then
    echo "OK: $2"
  else
    echo "FAIL (expected pass): $2"
    exit 1
  fi
}

fail() {
  PR_BODY="$1"
  if bash "$script"; then
    echo "FAIL (expected fail): $2"
    exit 1
  else
    echo "OK rejected: $2"
  fi
}

pass "Closes #196" "Closes #N"
pass "fixes #42" "fixes #N"
pass "Closes part of #210" "Closes part of #N"
pass "See also Fixes part of #210 in epic." "Fixes part of #N in prose"
fail "No issue link here." "missing reference"

echo "All traceability smoke tests passed."
