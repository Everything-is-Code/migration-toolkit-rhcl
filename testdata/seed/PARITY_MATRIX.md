# Seed catalog parity matrix

Epic: [#278](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/278)  
SDD change: `3scale-pattern-parity-validation` (rhcl-sdd store)

Automated **config parity** (P1): frozen 3scale export → `ConversionService.convert` → YAML fragments in [`expectations.yaml`](./expectations.yaml).  
**Runtime parity** (P3): cluster HTTP probes — separate issues #286–#288.

## Infrastructure issues (program)

| Phase | Issue | Status | Deliverable |
|-------|-------|--------|-------------|
| Epic | [#278](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/278) | Open | Program tracking |
| P1 | [#279](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/279) manifest + drift guard | PR [#290](https://github.com/Everything-is-Code/migration-toolkit-rhcl/pull/290) | `manifest.yaml`, `SeedCatalogIntegrityTest` |
| P1 | [#282](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/282) shared expectations | PR [#290](https://github.com/Everything-is-Code/migration-toolkit-rhcl/pull/290) | `expectations.yaml` |
| P1 | [#280](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/280) frozen exports | Open | `testdata/exports/*.json`, `refresh-seed-exports.sh` |
| P1 | [#281](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/281) conversion IT | Open | `SeedCatalogConversionIT` |
| P1 | [#284](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/284) CI docs | Open | `STATUS_CHECK_MATRIX.md` note |
| P2 | [#283](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/283) Playwright full catalog | Open | `yaml-expectations.ts` ← YAML |
| P2 | [#285](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/285) nightly E2E | Open | scheduled workflow |
| P3 | [#286](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/286)–[#288](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/288) | Open | runtime + profile matrix |
| Prereq | [#291](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/291) | Open | 3scaleextract chain seeder defaults |

## Per-product parity (assignable)

Each `rhcl_seed_*` product gets a **dedicated GitHub issue** for team assignment.

**Create issues:** run from repo root (requires `gh` auth):

```bash
chmod +x scripts/create-parity-product-issues.sh
./scripts/create-parity-product-issues.sh
```

Or use the issue template: **Seed parity — product slice** (`.github/ISSUE_TEMPLATE/seed-parity-product.yml`).

| Product issue | `system_name` | Policy / pattern | Export file |
|---------------|---------------|------------------|-------------|
| [#292](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/292) | `rhcl_seed_cors` | cors | `exports/rhcl_seed_cors.json` |
| [#293](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/293) | `rhcl_seed_headers` | headers | `exports/rhcl_seed_headers.json` |
| [#294](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/294) | `rhcl_seed_header_modification` | header_modification | `exports/rhcl_seed_header_modification.json` |
| [#295](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/295) | `rhcl_seed_ip_check` | ip_check | `exports/rhcl_seed_ip_check.json` |
| [#296](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/296) | `rhcl_seed_edge_limiting` | edge_limiting | `exports/rhcl_seed_edge_limiting.json` |
| [#297](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/297) | `rhcl_seed_token_introspection` | token_introspection | `exports/rhcl_seed_token_introspection.json` |
| [#298](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/298) | `rhcl_seed_app_id` | app_id auth | `exports/rhcl_seed_app_id.json` |
| [#299](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/299) | `rhcl_seed_logging` | logging | `exports/rhcl_seed_logging.json` |
| [#300](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/300) | `rhcl_seed_anonymous` | default_credentials | `exports/rhcl_seed_anonymous.json` |
| [#301](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/301) | `rhcl_seed_url_rewriting` | url_rewriting | `exports/rhcl_seed_url_rewriting.json` |
| [#302](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/302) | `rhcl_seed_auth_caching` | caching + OIDC | `exports/rhcl_seed_auth_caching.json` |
| [#303](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/303) | `rhcl_seed_jwt_claim_check` | jwt_claim_check + OIDC | `exports/rhcl_seed_jwt_claim_check.json` |
| [#304](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/304) | `rhcl_seed_upstream_connection` | upstream_connection | `exports/rhcl_seed_upstream_connection.json` |
| [#305](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/305) | `rhcl_seed_content_limits` | payload_limits | `exports/rhcl_seed_content_limits.json` |
| [#306](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/306) | `rhcl_seed_retry` | retry | `exports/rhcl_seed_retry.json` |
| [#307](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/307) | `rhcl_seed_keycloak_roles` | keycloak_role_check + OIDC | `exports/rhcl_seed_keycloak_roles.json` |
| [#308](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/308) | `rhcl_seed_oidc_jwt` | OIDC only | `exports/rhcl_seed_oidc_jwt.json` |
| [#309](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/309) | `rhcl_seed_claim_role_chain` | jwt_claim + keycloak chain | `exports/rhcl_seed_claim_role_chain.json` |
| [#310](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/310) | `rhcl_seed_claim_cache_chain` | jwt_claim + caching chain | `exports/rhcl_seed_claim_cache_chain.json` |
| [#311](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/311) | `rhcl_seed_auth_chain` | jwt_claim + ip_check chain | `exports/rhcl_seed_auth_chain.json` |
| [#312](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/312) | `rhcl_seed_multi_backend` | 3 backends | `exports/rhcl_seed_multi_backend.json` |

Expectations for all products: [`expectations.yaml`](./expectations.yaml).

## Conventions

| Path | Role |
|------|------|
| [`catalog.yaml`](./catalog.yaml) | Seeder + product definitions |
| [`manifest.yaml`](./manifest.yaml) | Version + optional cluster profile overrides |
| [`expectations.yaml`](./expectations.yaml) | YAML fragment contract (BE IT + Playwright) |
| [`../exports/`](../exports/) | Frozen `ApiService` JSON per `system_name` |
| [`../../scripts/refresh-seed-exports.sh`](../../scripts/refresh-seed-exports.sh) | Manual refresh (lab `THREESCALE_*`) |

## Workflow for implementers

1. Pick a **product issue** (create via `scripts/create-parity-product-issues.sh`) or parent #280 / #281.
2. Seed lab tenant: `./scripts/seed-rhcl-cases.sh` (after #291 if chain products).
3. Refresh export: `./scripts/refresh-seed-exports.sh` (backend on `:8080`).
4. Tighten `expectations.yaml` fragments if conversion output differs.
5. Ensure `SeedCatalogConversionIT` passes for your product.
6. Close product issue; when **all** products green, close #280 and #281.

## Tests

```bash
cd backend && mvn -Dtest=SeedCatalogIntegrityTest test
cd backend && mvn -Dtest=SeedCatalogConversionIT test   # after #281
```
