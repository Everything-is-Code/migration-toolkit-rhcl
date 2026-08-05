#!/usr/bin/env bash
# Sourceable: exports VERSION (semver) and VERSION_V (v-prefixed) from Chart.yaml.
set -euo pipefail
# shellcheck source=scripts/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
_CHART="${ROOT}/helm/migration-toolkit-rhcl/Chart.yaml"
if [[ ! -f "${_CHART}" ]]; then
	die "Chart not found: ${_CHART}"
fi
VERSION="$(grep '^version:' "${_CHART}" | awk '{print $2}' | tr -d '"')"
if [[ -z "${VERSION}" ]]; then
	die "Could not read version from ${_CHART}"
fi
export VERSION VERSION_V="v${VERSION}"
