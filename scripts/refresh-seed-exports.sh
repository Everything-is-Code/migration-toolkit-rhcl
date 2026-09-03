#!/usr/bin/env bash
# Refresh frozen ApiService export JSON for rhcl_seed_* products (#280).
# Requires: running backend, seeded lab tenant, THREESCALE_* env vars.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CATALOG="${ROOT}/testdata/seed/catalog.yaml"
OUT_DIR="${ROOT}/testdata/exports"
API_BASE="${MIGRATION_API_URL:-http://localhost:8080}"

usage() {
  cat <<EOF
Usage: $(basename "$0")

Writes testdata/exports/{system_name}.json for each product in catalog.yaml.

Env:
  THREESCALE_ADMIN_URL      Required — 3scale Admin Portal URL
  THREESCALE_ACCESS_TOKEN   Required — Personal Access Token
  MIGRATION_API_URL         Backend base URL (default: http://localhost:8080)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "${THREESCALE_ADMIN_URL:-}" || -z "${THREESCALE_ACCESS_TOKEN:-}" ]]; then
  echo "error: set THREESCALE_ADMIN_URL and THREESCALE_ACCESS_TOKEN" >&2
  usage
  exit 1
fi

if [[ ! -f "${CATALOG}" ]]; then
  echo "error: catalog not found: ${CATALOG}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

mapfile -t SYSTEM_NAMES < <(grep -E '^\s+system_name: rhcl_seed_' "${CATALOG}" | awk '{print $2}')

for name in "${SYSTEM_NAMES[@]}"; do
  echo "Resolving service id for ${name}..."
  id="$(curl -sf -G "${API_BASE}/api/services" \
    --data-urlencode "url=${THREESCALE_ADMIN_URL}" \
    --data-urlencode "page=1" \
    --data-urlencode "perPage=100" \
    -H "Authorization: Bearer ${THREESCALE_ACCESS_TOKEN}" \
    | python3 -c "import json,sys; data=json.load(sys.stdin); print(next((s['id'] for s in data.get('items',[]) if s.get('systemName')=='${name}'), ''))")"

  if [[ -z "${id}" ]]; then
    echo "error: no service with systemName=${name} in tenant (seed catalog first)" >&2
    exit 1
  fi

  out="${OUT_DIR}/${name}.json"
  echo "Exporting ${name} (id=${id}) -> ${out}"
  curl -sf -G "${API_BASE}/api/services/${id}" \
    --data-urlencode "url=${THREESCALE_ADMIN_URL}" \
    -H "Authorization: Bearer ${THREESCALE_ACCESS_TOKEN}" \
    -o "${out}"
done

echo "Done. ${#SYSTEM_NAMES[@]} export(s) in ${OUT_DIR}"
