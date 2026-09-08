#!/usr/bin/env bash
# Create per-product parity issues for epic #278 (one issue per rhcl_seed_* product).
# Idempotent-ish: skips if an open issue with the same title already exists.
set -euo pipefail

REPO="${GITHUB_REPO:-Everything-is-Code/migration-toolkit-rhcl}"

PRODUCTS=(
"rhcl_seed_cors:cors"
"rhcl_seed_headers:headers"
"rhcl_seed_header_modification:header_modification"
"rhcl_seed_ip_check:ip_check"
"rhcl_seed_edge_limiting:edge_limiting"
"rhcl_seed_token_introspection:token_introspection"
"rhcl_seed_app_id:app_id auth"
"rhcl_seed_logging:logging"
"rhcl_seed_anonymous:default_credentials"
"rhcl_seed_url_rewriting:url_rewriting"
"rhcl_seed_auth_caching:caching + OIDC"
"rhcl_seed_jwt_claim_check:jwt_claim_check + OIDC"
"rhcl_seed_upstream_connection:upstream_connection"
"rhcl_seed_content_limits:payload_limits"
"rhcl_seed_retry:retry"
"rhcl_seed_keycloak_roles:keycloak_role_check + OIDC"
"rhcl_seed_oidc_jwt:OIDC JWT only"
"rhcl_seed_claim_role_chain:jwt_claim + keycloak chain"
"rhcl_seed_claim_cache_chain:jwt_claim + caching chain"
"rhcl_seed_auth_chain:jwt_claim + ip_check chain"
"rhcl_seed_multi_backend:multi-backend routing"
)

for entry in "${PRODUCTS[@]}"; do
  name="${entry%%:*}"
  policy="${entry#*:}"
  title="parity(${name}): export fixture + conversion IT"

  if gh issue list --repo "${REPO}" --state open --search "${title} in:title" --json number --jq '.[0].number' | grep -q .; then
    echo "SKIP (exists): ${title}"
    continue
  fi

  url=$(gh issue create --repo "${REPO}" \
    --title "${title}" \
    --label "parity-matrix" --label "area/testing" --label "testing" \
    --body "$(cat <<EOF
Part of epic #278.

## Product
\`${name}\` — **${policy}**

## Parent batch issues
- #280 — frozen export JSON (all products)
- #281 — \`SeedCatalogConversionIT\` (all products)

## Acceptance criteria
- [ ] \`testdata/exports/${name}.json\` committed
- [ ] \`testdata/seed/expectations.yaml\` → \`${name}\` fragments match conversion output
- [ ] \`SeedCatalogConversionIT\` passes for this product
- [ ] (P2) Playwright expectations aligned (#283) if applicable

## References
- \`testdata/seed/PARITY_MATRIX.md\`
- \`scripts/refresh-seed-exports.sh\`

Part of #278
EOF
)")
  echo "Created: ${url}"
done

echo "Done. Update PARITY_MATRIX.md product table with new issue numbers."
