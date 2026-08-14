# 3scale seed fixtures for RHCL conversion cases

Reusable lab data for the Migration Toolkit: **one product + one backend + one focused policy** (or auth mode) per implemented conversion path.

## Catalog

| Product `system_name` | Auth | Policy | Conversion under test |
|-----------------------|------|--------|------------------------|
| `rhcl_seed_cors` | api_key | `cors` | ResponseHeaderModifier / native CORS |
| `rhcl_seed_headers` | api_key | `headers` | HTTPRoute HeaderModifier |
| `rhcl_seed_header_modification` | api_key | `header_modification` | same as headers (alias) |
| `rhcl_seed_ip_check` | api_key | `ip_check` | AuthorizationPolicy / AuthPolicy OPA |
| `rhcl_seed_edge_limiting` | api_key | `edge_limiting` | RateLimitPolicy |
| `rhcl_seed_token_introspection` | api_key | `token_introspection` | AuthPolicy oauth2Introspection |
| `rhcl_seed_app_id` | app_id | _(none)_ | Application Secrets |

Shared backend: `rhcl_seed_upstream` → `https://httpbin.org:443`.

Source of truth: [`catalog.yaml`](./catalog.yaml).

## Tooling

Uses [`threescale-seed`](https://github.com/Everything-is-Code/3scaleextract) with `--fixtures`:

```bash
# From this repo
./scripts/seed-rhcl-cases.sh --list
./scripts/seed-rhcl-cases.sh --dry-run
./scripts/seed-rhcl-cases.sh
```

Or manually:

```bash
export THREESCALE_ADMIN_URL='https://<tenant>-admin.3scale.net'
export THREESCALE_ACCESS_TOKEN='...'

# Build once (sibling checkout or clone)
go build -o /tmp/threescale-seed \
  ../3scaleextract/cmd/threescale-seed   # adjust path

/tmp/threescale-seed \
  --fixtures testdata/seed/catalog.yaml \
  --skip-existing
```

## Manual test flow in the toolkit UI

1. Seed the tenant (above).
2. Open the toolkit → connect to the same 3scale Admin URL + Bearer token.
3. Export / select **one** `rhcl_seed_*` product at a time.
4. Run Compatibility + Conversion and verify the YAML for that case.
5. Optional: set cluster profile `auto` / `ocp-4.19` / `ocp-4.21` to exercise CORS native vs fallback.

## Extending

Add a product block to `catalog.yaml` (keep **one policy** per product for isolation). Rebuild is not required for catalog-only changes — only re-run seed.
