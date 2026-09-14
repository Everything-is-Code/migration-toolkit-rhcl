#!/usr/bin/env bash
# List product system_name values from testdata/seed/catalog.yaml (products: block only).
# Excludes backends (rhcl_seed_upstream, etc.) and nested plan system_name entries.
set -euo pipefail

list_seed_catalog_products() {
  local catalog="${1:?catalog path required}"
  if [[ ! -f "${catalog}" ]]; then
    echo "error: catalog not found: ${catalog}" >&2
    return 1
  fi
  awk '
    /^products:/ { in_products = 1; next }
    in_products && /^  - system_name: / {
      sub(/^  - system_name: /, "")
      print
    }
  ' "${catalog}"
}
