#!/usr/bin/env bash
# Seed RHCL migration conversion cases into a 3scale tenant via threescale-seed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CATALOG="${ROOT}/testdata/seed/catalog.yaml"
EXTRACT_DIR="${THREESCALE_SEED_SRC:-${ROOT}/../3scaleextract}"
SEED_BIN="${THREESCALE_SEED_BIN:-}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--list|--dry-run] [extra threescale-seed flags...]

Loads testdata/seed/catalog.yaml (1 product + 1 backend + 1 policy per case).

Env:
  THREESCALE_ADMIN_URL      3scale Admin Portal URL (required unless --list)
  THREESCALE_ACCESS_TOKEN   Personal Access Token (required unless --list)
  THREESCALE_SEED_SRC       Path to 3scaleextract checkout (default: ../3scaleextract)
  THREESCALE_SEED_BIN       Prebuilt threescale-seed binary (optional)
EOF
}

ARGS=()
for arg in "$@"; do
  case "$arg" in
    -h|--help)
      usage
      exit 0
      ;;
    --list)
      ARGS+=(--list-fixtures)
      ;;
    *)
      ARGS+=("$arg")
      ;;
  esac
done

if [[ -z "${SEED_BIN}" ]]; then
  if [[ ! -d "${EXTRACT_DIR}" ]]; then
    echo "error: 3scaleextract not found at ${EXTRACT_DIR}" >&2
    echo "set THREESCALE_SEED_SRC or THREESCALE_SEED_BIN" >&2
    exit 1
  fi
  SEED_BIN="${EXTRACT_DIR}/bin/threescale-seed"
  if [[ ! -x "${SEED_BIN}" ]]; then
    echo "building threescale-seed..."
    (cd "${EXTRACT_DIR}" && go build -o bin/threescale-seed ./cmd/threescale-seed)
  fi
fi

exec "${SEED_BIN}" --fixtures "${CATALOG}" --skip-existing "${ARGS[@]}"
