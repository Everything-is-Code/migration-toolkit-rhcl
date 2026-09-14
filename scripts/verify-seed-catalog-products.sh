#!/usr/bin/env bash
# Assert scripts parse the same product ids as expectations.yaml (drift guard for shell tooling).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/seed-catalog-products.sh
source "${ROOT}/scripts/lib/seed-catalog-products.sh"

CATALOG="${ROOT}/testdata/seed/catalog.yaml"
EXPECTATIONS="${ROOT}/testdata/seed/expectations.yaml"

mapfile -t CATALOG_NAMES < <(list_seed_catalog_products "${CATALOG}")
mapfile -t EXPECTATION_NAMES < <(grep -E '^  rhcl_seed_' "${EXPECTATIONS}" | sed -E 's/^  ([^:]+):.*/\1/' | sort)

if [[ "${#CATALOG_NAMES[@]}" -eq 0 ]]; then
  echo "error: no products parsed from catalog (check scripts/lib/seed-catalog-products.sh)" >&2
  exit 1
fi

CATALOG_SORTED="$(printf '%s\n' "${CATALOG_NAMES[@]}" | sort)"
EXPECTATIONS_SORTED="$(printf '%s\n' "${EXPECTATION_NAMES[@]}")"

if [[ "${CATALOG_SORTED}" != "${EXPECTATIONS_SORTED}" ]]; then
  echo "error: catalog product list does not match expectations.yaml keys" >&2
  echo "--- catalog (${#CATALOG_NAMES[@]})" >&2
  printf '  %s\n' "${CATALOG_NAMES[@]}" >&2
  echo "--- expectations (${#EXPECTATION_NAMES[@]})" >&2
  printf '  %s\n' "${EXPECTATION_NAMES[@]}" >&2
  exit 1
fi

echo "OK: ${#CATALOG_NAMES[@]} seed catalog products align (catalog.yaml ↔ expectations.yaml)"
