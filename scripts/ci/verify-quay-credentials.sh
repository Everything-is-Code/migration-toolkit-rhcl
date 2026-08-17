#!/usr/bin/env bash
# Fail fast when Quay robot credentials are missing or cannot obtain push scope.
set -euo pipefail

REGISTRY="${QUAY_REGISTRY:-quay.io}"
NAMESPACE="${QUAY_NAMESPACE:-everythingascode}"
REPO="${1:-migration-toolkit-rhcl-backend}"

if [ -z "${QUAY_USERNAME:-}" ] || [ -z "${QUAY_PASSWORD:-}" ]; then
  echo "::error::QUAY_USERNAME and QUAY_PASSWORD must be set in repository secrets."
  echo "See README → Maintainer → Path B (Quay robot account)."
  exit 1
fi

echo "Verifying ${REGISTRY} credentials for ${NAMESPACE}/${REPO} ..."
echo "${QUAY_PASSWORD}" | docker login "${REGISTRY}" -u "${QUAY_USERNAME}" --password-stdin

SCOPE="repository:${NAMESPACE}/${REPO}:pull,push"
AUTH_URL="${REGISTRY}/v2/auth?service=${REGISTRY}&scope=${SCOPE}"
if ! curl -sf -u "${QUAY_USERNAME}:${QUAY_PASSWORD}" "https://${AUTH_URL}" >/dev/null; then
  echo "::error::Quay rejected credentials (401/403). Rotate the robot token at quay.io and update GitHub secrets QUAY_USERNAME / QUAY_PASSWORD."
  echo "Robot username is usually namespace+robotname (e.g. everythingascode+github-actions)."
  exit 1
fi

echo "Quay credentials OK for push scope on ${NAMESPACE}/${REPO}"
