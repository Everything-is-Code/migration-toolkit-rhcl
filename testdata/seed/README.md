# 3scale seed fixtures for RHCL conversion cases

Reusable lab data for the Migration Toolkit: **one product + one focused policy** (or auth / multi-backend pattern) per implemented conversion path.

## Catalog

| Product `system_name` | Auth | Policy / pattern | Conversion under test |
|-----------------------|------|------------------|------------------------|
| `rhcl_seed_cors` | api_key | `cors` | ResponseHeaderModifier / native CORS |
| `rhcl_seed_headers` | api_key | `headers` | HTTPRoute HeaderModifier |
| `rhcl_seed_header_modification` | api_key | `header_modification` | same as headers (alias) |
| `rhcl_seed_ip_check` | api_key | `ip_check` | AuthorizationPolicy / AuthPolicy OPA |
| `rhcl_seed_edge_limiting` | api_key | `edge_limiting` | RateLimitPolicy |
| `rhcl_seed_token_introspection` | api_key | `token_introspection` | AuthPolicy oauth2Introspection |
| `rhcl_seed_app_id` | app_id | _(none)_ | Application Secrets |
| `rhcl_seed_logging` | api_key | `logging` | Telemetry + EnvoyFilter logging |
| `rhcl_seed_anonymous` | api_key | `default_credentials` | AuthPolicy anonymous |
| `rhcl_seed_url_rewriting` | api_key | `url_rewriting` | EnvoyFilter Lua rewrite |
| `rhcl_seed_auth_caching` | oidc | `caching` | AuthPolicy JWT + cache block |
| `rhcl_seed_jwt_claim_check` | oidc | `jwt_claim_check` | AuthPolicy claim patternMatching |
| `rhcl_seed_upstream_connection` | api_key | `upstream_connection` | HTTPRoute timeouts |
| `rhcl_seed_content_limits` | api_key | `payload_limits` | EnvoyFilter content limits |
| `rhcl_seed_retry` | api_key | `retry` | HTTPRoute retry / EnvoyFilter fallback |
| `rhcl_seed_keycloak_roles` | oidc | `keycloak_role_check` | AuthPolicy role patterns |
| `rhcl_seed_oidc_jwt` | oidc | _(none)_ | AuthPolicy JWT only |
| `rhcl_seed_multi_backend` | api_key | 3 backends | Path-first multi-backend routing |

Shared upstream: `rhcl_seed_upstream` → `https://httpbin.org:443`.  
Multi-backend also uses `rhcl_seed_orders` + `rhcl_seed_catalog_be` (paths `/`, `/backend-2`, `/backend-3` from seeder order).

Source of truth: [`catalog.yaml`](./catalog.yaml).

### APIcast name notes

| Seed `policy_names` entry | RHCL converter accepts |
|---------------------------|-------------------------|
| `caching` | `caching` and `3scale_auth_caching` |
| `payload_limits` | `payload_limits` and `content_limits` |
| `default_credentials` | `default_credentials` and `anonymous_access` |

## Tooling

Uses [`threescale-seed`](https://github.com/Everything-is-Code/3scaleextract) with `--fixtures`:

```bash
# From this repo — list / dry-run only until you decide to load
./scripts/seed-rhcl-cases.sh --list
./scripts/seed-rhcl-cases.sh --dry-run

# Load into a lab tenant (when ready)
export THREESCALE_ADMIN_URL='https://<tenant>-admin.3scale.net'
export THREESCALE_ACCESS_TOKEN='...'
./scripts/seed-rhcl-cases.sh
```

Or manually:

```bash
export THREESCALE_ADMIN_URL='https://<tenant>-admin.3scale.net'
export THREESCALE_ACCESS_TOKEN='...'

go build -o /tmp/threescale-seed \
    ../3scaleextract/cmd/threescale-seed   # adjust path

/tmp/threescale-seed \
  --fixtures testdata/seed/catalog.yaml \
  --skip-existing
```

Policy default configs live in `3scaleextract/internal/seed/policies.go` (`policyConfigurations`). Catalog-only changes do not require rebuilding until new policy names need configs.

## Manual test flow in the toolkit UI

1. Seed the tenant (above) when instructed.
2. Open the toolkit → connect to the same 3scale Admin URL + Bearer token.
3. Export / select **one** `rhcl_seed_*` product at a time.
4. Run Compatibility + Conversion and verify the YAML for that case.
5. Optional: set cluster profile `auto` / `ocp-4.19` / `ocp-4.21` to exercise CORS / retry / timeout capability gates.
6. For OIDC products, issuer URLs are lab placeholders (`sso.example.com`) — AuthPolicy still emits; replace before real apply.

## Extending

Add a product block to `catalog.yaml` (keep **one policy** per product for isolation, except intentional multi-backend / auth-only cases). Add a matching entry in `policyConfigurations` when the policy needs non-empty config. Rebuild `threescale-seed` only when `policies.go` changes.
