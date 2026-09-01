# User guide

End-user workflow, features, and architecture overview for the Migration Toolkit.

## Architecture

```
               +----------------------+
               |      Web UI          |
               |  (React/PatternFly)  |
               +----------+-----------+
                          |
                    REST API (JSON)
                          |
        +-----------------+------------------+
        |     Quarkus Backend (Java 21)      |
        |                                    |
        | ① 3scale Export                    |
        | ② Parser / Compatibility Checker   |
        | ③ Converter (YAML generators + contributors) |
        | ④ Validation                       |
        | ⑤ Package Download (ZIP)           |
        | ⑥ Import / Apply to Cluster        |
        | ⑦ ZIP Import Conversion History    |
        | ⑧ Gateway Info                     |
        | ⑨ Namespace Setup                  |
        +-----------------+------------------+
                    |               |
              PostgreSQL         OpenShift /
            (CrunchyData)      Kubernetes API
                    |
          Connectivity Link YAML
         (Gateway / HTTPRoute / AuthPolicy / …)
```

**Technology stack**

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, PatternFly 5, Vite, TypeScript, react-i18next, Vitest |
| Backend | Quarkus 3.27.x (Java 21), RESTEasy Reactive, Hibernate ORM Panache |
| Database | PostgreSQL (Helm chart or CrunchyData Operator) |
| Kubernetes client | Fabric8 Kubernetes Client 6.7.x |
| OpenAPI | SmallRye OpenAPI + Swagger UI |
| DB migrations | Flyway V1–V9 |
| Deployment | Helm (preferred), OpenShift S2I, Quay images |

YAML conversion is **in-repo** (`ConversionService` + `service/generator/`). See [Conversion architecture](conversion-architecture.md).

## Workflow

```
① Configure 3scale connection (URL / Access Token / Tenant / Namespace)
      ↓
② Fetch API list (Service / Backend / MappingRule / Metrics / Policies / Auth)
      ↓
③ Select APIs to migrate
      ↓
④ Compatibility Check (scoring: JWT / Rewrite / Lua Policy / SOAP, etc.)
      ↓
⑤ Generate YAML (in-repo ConversionService + generator registry)
      ↓
⑥ Preview / edit YAML in browser
      ↓
⑦ Validation (YAML syntax / CRD / Namespace / Secret / Reference)
      ↓
⑧ Download as ZIP
      ↓
⑨ ZIP Import → apply to cluster
     · Auto-create Role/RoleBinding in target Namespace
     · Apply via Server-Side Apply
     · Export applied resources and save to history
      ↓
⑩ Review conversion history (success/failure counts, error details, re-download ZIP)
```

## Features

### 1. 3scale connection setup

Input: Admin Portal URL, Personal Access Token, tenant (optional), target namespace.

Backend 3scale calls include `GET /admin/api/services.json`, backends, proxy configs, policies.

### 2. API list

Service basics, backend private endpoint, mapping rules, metrics, policies, auth type (API Key / OIDC / JWT, …).

### 3. Compatibility check

| Result | Meaning |
|--------|---------|
| ✔ SUPPORTED | Migrates as-is |
| ⚠ WARNING | Manual adjustment required |
| × UNSUPPORTED | Not supported |

Migration score 0–100% summarizes overall effort.

### 4. YAML generation

Core package files (always or conditional — full matrix in [conversion architecture](conversion-architecture.md)):

```
{service-name}/
  gateway.yaml, httproute.yaml, policy.yaml, secret.yaml, configmap.yaml
  apiproduct.yaml, README.md
  apikey.yaml, serviceentry.yaml, destinationrule.yaml  (conditional)
  telemetry.yaml, envoyfilter-*.yaml, authorizationpolicy.yaml, ratelimitpolicy.yaml
  tlspolicy.yaml, dnspolicy.yaml  (opt-in)
```

Replace `REPLACE_ME` in `secret.yaml` before apply.

**TLSPolicy (opt-in):** cert-manager `issuerRef`; Gateway https listener references `{name}-tls` Secret (created by cert-manager after apply).

**DNSPolicy (opt-in):** Gateway listeners get `hostname`; `GET /api/cluster/domain` prefills `{kebabName}.{clusterDomain}` (domain already includes `apps.`).

**IP check:** Prefer `authorizationPolicy` mode (Istio `remoteIpBlocks`) for end-client IP allowlists; OPA mode uses Authorino `input.source.address`.

**External backends:** URL scheme from 3scale (`http://` → port 80, no TLS; `https://` → 443 + TLS) drives `DestinationRule` / `ServiceEntry` consistency.

### 5–7. Preview, validation, ZIP download

In-browser YAML editor; syntax/CRD/namespace/reference/secret checks; ZIP download (e.g. `customer-api.zip`).

### 8. ZIP import / apply

Upload ZIP, edit YAML, bulk namespace replace, cluster apply via backend. Auto RBAC (`migration-tool-istio-manager`), apiVersion normalization, history export. Curl test command with custom path.

### 9. Conversion history

Records for CONVERT and IMPORT runs: status, counts, failure details, re-download ZIP. Bulk delete supported.

### 10–11. Gateway info & namespace setup

Live Gateway/Listener info; namespace bootstrap for Connectivity Link.

### 12. Settings & supported policies

- `/settings` — cluster profile defaults (`app_settings`)
- `/settings/policies` — which 3scale policies convert today

## Repository layout

```
migration-toolkit-rhcl/
├── backend/          # Quarkus API, service/generator/, Flyway
├── frontend/         # React SPA (pages/ + components/)
├── helm/             # Preferred install
├── deploy/           # OpenShift S2I + install.sh
├── documentation/    # This guide set
├── docs/             # Helm GitHub Pages index only
├── examples/gitops/
├── scripts/, testdata/
├── CONTRIBUTING.md, AGENTS.md
```

## Internationalization (i18n)

**Frontend:** `react-i18next`, `locales/en.json` + `ja.json`, JA/EN masthead toggle.

**Backend:** `ResourceBundle` (`messages_en.properties` / `messages_ja.properties`). `Accept-Language` from the Axios client drives user-facing API messages.

## Screenshots

See [README](../README.md#screenshots) for UI screenshots.
