#!/usr/bin/env bash
# Sourceable helpers. Do not execute directly.
set -euo pipefail

# Repo root: scripts/lib -> scripts -> repo root
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export ROOT

die() {
	echo "ERROR: $*" >&2
	exit 1
}

log() {
	echo "$*"
}
