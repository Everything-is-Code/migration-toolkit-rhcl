# Migration Toolkit for Red Hat Connectivity Link

A GUI toolkit for migrating from 3scale to Red Hat Connectivity Link.  
Built with a Quarkus backend and a React/PatternFly frontend.

---

## Table of Contents

- [Screenshots](#screenshots)
- [Prerequisites & Required Tools](#prerequisites--required-tools)
- [Quick Start](#quick-start)
  - [Install with Helm (any cluster)](#install-with-helm-any-cluster)
  - [Full Deploy to OpenShift (S2I / legacy)](#full-deploy-to-openshift)
- [Architecture](#architecture)
- [Workflow](#workflow)
- [Features](#features)
- [Directory Structure](#directory-structure)
- [API Reference](#api-reference)
- [Data Model](#data-model)
- [Internationalization (i18n)](#internationalization-i18n)
- [Testing](#testing)
- [Maintainer: make this repository operational (post-merge)](#maintainer-make-this-repository-operational-post-merge)
- [Contributing](CONTRIBUTING.md) · [Agents](AGENTS.md) · [Security](SECURITY.md)
- [日本語ドキュメント](#日本語ドキュメント)

---

## Screenshots

| 3scale Connection Setup | Compatibility Check |
|:-:|:-:|
| ![3scale Connection](.claude/images/connect.png) | ![Compatibility Check](.claude/images/apilist.png) |

| YAML Generation | YAML Preview |
|:-:|:-:|
| ![YAML Generation](.claude/images/yaml.png) | ![YAML Preview](.claude/images/preview.png) |

| Validation | Download |
|:-:|:-:|
| ![Validation](.claude/images/validation.png) | ![Download](.claude/images/download.png) |

| ZIP Import / CL Config | curl Connectivity Test |
|:-:|:-:|
| ![ZIP Import](.claude/images/import.png) | ![curl Test](.claude/images/rhcltest.png) |

---

## Prerequisites & Required Tools

### Required tools

Related conversion tooling (external reference; not vendored in this repository):

- **[from-3scale-to-connectivity-link](https://github.com/Everything-is-Code/from-3scale-to-connectivity-link)** — 3scale → Connectivity Link / Developer Hub adapter.

Helm chart packaging, Quay container images, and GitHub Actions CI/CD were validated together with this adapter on the contribution fork [`Everything-is-Code/migration-toolkit-rhcl`](https://github.com/Everything-is-Code/migration-toolkit-rhcl).

### Local Development

| Tool | Version | Purpose |
|------|---------|---------|
| Java (OpenJDK) | 21+ | Backend build |
| Apache Maven | 3.9.x+ | Backend build |
| Node.js | 22 | Frontend build |
| npm | 9+ | Frontend dependency management |
| Docker / Podman | Latest | Container image build (local testing) |

### OpenShift Cluster

| Tool / Component | Version | Purpose |
|-----------------|---------|---------|
| OpenShift Container Platform | 4.14+ | Target deployment cluster |
| `oc` CLI | Matching cluster version | Cluster operations |
| CrunchyData PostgreSQL Operator | Latest | Database management (pre-install from OperatorHub) |
| Sail Operator (Istio) | Latest | Gateway API implementation — auto-installed by `install.sh` |
| Red Hat Connectivity Link (`rhcl-operator`) | Latest | Migration target component — auto-installed by `install.sh` |

> **Note**: Install the CrunchyData PostgreSQL Operator into the `openshift-operators`  
> namespace **before** running the install script. The script detects it automatically.
>
> Sail Operator and Red Hat Connectivity Link (`rhcl-operator`, Red Hat Operators catalog)
> are installed automatically by `install.sh` / `install.sh --kuadrant-only`. Use the
> productized `rhcl-operator`, not the community `kuadrant-operator` — the community
> build lacks the DevPortal CRDs (`APIKey` / `APIProduct`) and only serves `AuthPolicy`
> as `v1beta2`, not `v1`.

### 3scale Environment

- 3scale Admin Portal URL and a Personal Access Token

---

## Quick Start

### Install with Helm (any cluster)

Preferred path for OpenShift (and Kubernetes with Routes available): install pre-built images via Helm. Default images are public on Quay:

- `quay.io/everythingascode/migration-toolkit-rhcl-backend:latest` (also tagged `v0.1.0`)
- `quay.io/everythingascode/migration-toolkit-rhcl-frontend:latest` (also tagged `v0.1.0`)

**Prerequisites:** OpenShift 4.14+ (or compatible), `helm` 3.x, `oc`, and Red Hat Connectivity Link / Kuadrant as required by the toolkit. The chart deploys an embedded PostgreSQL (`registry.redhat.io/rhel9/postgresql-15`) unless you point at an external DB.

From the published Helm repository (GitHub Pages):

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

From a local clone of this repository:

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

Override image tags or repositories if needed:

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace \
  --set backend.image.tag=latest \
  --set frontend.image.tag=latest
```

After install, get the frontend Route:

```bash
oc -n migration-toolkit get route
```

For OpenShift GitOps / Argo CD, copy [`examples/gitops/application.yaml`](examples/gitops/application.yaml) into your GitOps repo and set `repoURL` to this repository (or upstream after merge) with `path: helm/migration-toolkit-rhcl`.

> Until the upstream maintainer enables GitHub Pages on `/docs`, you can keep using the validated Pages Helm repo: `https://everything-is-code.github.io/migration-toolkit-rhcl/`.

### Full Deploy to OpenShift

Legacy path: in-cluster S2I builds via `deploy/install.sh` (unchanged). Prefer Helm above for any-cluster installs with published images.

```bash
# Deploy to a specific namespace (default: migration-toolkit)
NAMESPACE=migration-toolkit ./deploy/install.sh
# Backend only
NAMESPACE=migration-toolkit ./deploy/install.sh --backend-only

# Frontend only
NAMESPACE=migration-toolkit ./deploy/install.sh --frontend-only

# Database only
NAMESPACE=migration-toolkit ./deploy/install.sh --db-only
```

The install script handles:

1. Namespace creation
2. Sail Operator (Istio) + Red Hat Connectivity Link (`rhcl-operator`) install & wait for their CRDs
3. Waiting for CrunchyData PostgreSQL Operator
4. PostgreSQL cluster creation (including SCC)
5. Backend Maven build → S2I → deployment
6. Frontend npm build → S2I (nginx) → deployment
7. Printing access URLs

> **Rebuilding the frontend manually** (outside of `install.sh`): `npm run build` empties
> `frontend/build/` (`vite.config.ts` → `emptyOutDir`), which deletes the copied
> `nginx-default-cfg/` directory. Before running a binary S2I build
> (`oc start-build ... --from-dir=frontend/build`), re-copy it — otherwise the deployed
> image ships without the `/api/` reverse-proxy config and every backend call 404s:
> ```bash
> mkdir -p frontend/build/nginx-default-cfg
> cp frontend/nginx-default-cfg/api-proxy.conf frontend/build/nginx-default-cfg/api-proxy.conf
> ```
> `install.sh`'s `deploy_frontend` already does this for you.

### Language Selection

```bash
# English (default)
./deploy/install.sh

# Run in Japanese
INSTALL_LANG=ja ./deploy/install.sh
```

> `install.sh` no longer inspects the system locale (`$LANG`). It is English by
> default; pass `INSTALL_LANG=ja` explicitly to switch to Japanese.

### Local Development

```bash
# Start backend (PostgreSQL must be running on localhost:5432)
cd backend
mvn quarkus:dev

# Start frontend (separate terminal)
cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

CORS defaults to `http://localhost:5173`, `http://localhost:3000`, and
`http://localhost:8080`. For OpenShift or temporary demos, override with
`CORS_ORIGINS` or `QUARKUS_HTTP_CORS_ORIGINS` (comma-separated origins).
Export API tokens must be sent as `Authorization: Bearer <token>` (never as
query `accessToken` / `access_token`). Existing POST bodies may still include
`accessToken`.

---

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
        | ③ Converter (YAML Generator)       |
        | ④ Validation                       |
        | ⑤ Package Download (ZIP)           |
        | ⑥ Import / Apply to Cluster        |
        |    (auto RBAC provisioning)        |
        | ⑦ ZIP Import Conversion History    |
        | ⑧ Gateway Info                     |
        | ⑨ Namespace Setup                  |
        +-----------------+------------------+
                    |               |
       from-3scale-to-connectivity  PostgreSQL
            -link (Adapter)         (CrunchyData)
                    |
          Connectivity Link YAML
         (Gateway / HTTPRoute / AuthPolicy /
          RateLimitPolicy / DestinationRule /
          ServiceEntry / Secret / ConfigMap)
```

**Technology Stack**

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, PatternFly 5, Vite, TypeScript, react-i18next |
| Backend | Quarkus 3.27.5.1 (Java 21), RESTEasy Reactive, Hibernate ORM Panache |
| Database | PostgreSQL (managed by CrunchyData Operator) |
| Kubernetes client | Fabric8 Kubernetes Client 6.7.x |
| OpenAPI | SmallRye OpenAPI + Swagger UI (`/q/swagger-ui`) |
| DB Migrations | Flyway (V1–V3) |
| Deployment | OpenShift S2I, nginx (frontend static serving) |

---

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
⑤ Generate YAML (via from-3scale-to-connectivity-link adapter)
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
     · Export applied resources (-o yaml equivalent) and save to history
      ↓
⑩ Review conversion history (success/failure counts, error details, re-download ZIP)
```

---

## Features

### 1. 3scale Connection Setup

Input fields:
- 3scale Admin Portal URL
- Personal Access Token
- Tenant name (optional)
- Target OpenShift Namespace

3scale APIs called by the backend:
```
GET /admin/api/services.json
GET /admin/api/backends.json
GET /admin/api/proxy_configs
GET /admin/api/policies
```

### 2. API List

Information retrieved:
- Service basics (ID / name / deployment option)
- Backend (Private Endpoint)
- Mapping Rules
- Metrics
- Policies
- Authentication type (API Key / OIDC / JWT, etc.)

### 3. Compatibility Check

Evaluates whether each API policy/feature can be migrated to Connectivity Link:

| Result | Meaning |
|--------|---------|
| ✔ SUPPORTED | Migrates as-is |
| ⚠ WARNING | Manual adjustment required |
| × UNSUPPORTED | Not supported (requires custom handling) |

A Migration Score (0–100%) quantifies the overall migration effort.

### 4. YAML Generation

Resources generated via the `from-3scale-to-connectivity-link` adapter:

```
{service-name}/
  gateway.yaml         # Kuadrant Gateway
  httproute.yaml       # HTTPRoute
  policy.yaml          # AuthPolicy / RateLimitPolicy
  tlspolicy.yaml       # Kuadrant TLSPolicy (opt-in; cert-manager issuerRef)
  dnspolicy.yaml       # Kuadrant DNSPolicy (opt-in; optional providerRefs)
  secret.yaml          # Credentials (REPLACE_ME placeholders)
  configmap.yaml       # Configuration values
  destinationrule.yaml # Istio DestinationRule (TLS)
  serviceentry.yaml    # Istio ServiceEntry (external service)
  README.md
```

> Replace `REPLACE_ME` placeholders in `secret.yaml` with actual values before applying.

**TLSPolicy (opt-in)**: When "Generate TLSPolicy" is enabled on the Conversion page, the package
includes `tlspolicy.yaml` targeting `{name}-gateway` with a cert-manager `issuerRef`
(UI prefills `ClusterIssuer` / `letsencrypt-prod`). The Gateway https listener continues to
reference Secret `{name}-tls`; cert-manager creates that Secret after apply. A raw Certificate
CR is **not** generated. The ClusterIssuer must already exist on the cluster.

**DNSPolicy (opt-in)**: When "Generate DNSPolicy + Gateway hostname" is enabled, both Gateway
`http` and `https` listeners get `hostname: {dnsHostname}` (UI prefills
`{kebabName}.{clusterDomain}` from `GET /api/cluster/domain` — the domain already includes
`apps.`, so do not add another `apps.`). The package includes `dnspolicy.yaml` targeting
`{name}-gateway`. If a DNS provider Secret name is provided, `providerRefs: [{ name }]` is
included; if blank, `providerRefs` is omitted and Kuadrant uses the cluster
`kuadrant.io/default-provider=true` Secret. Provider credentials are never written into the package.

**External backend detection**: If the "backend is an external service" checkbox is left
unchecked, the backend type is no longer inferred only from that checkbox — it also falls
back to the backend URL already registered in 3scale (`service.backends[0].privateEndpoint`),
so `httproute.yaml` / `serviceentry.yaml` / `configmap.yaml` stay consistent even when the
checkbox isn't used. The backend port and whether `DestinationRule` originates TLS are now
derived from that URL's scheme (`http://` → port 80, no TLS / `https://` → port 443, TLS
`SIMPLE`) instead of being hardcoded to `443` + TLS, which previously broke plain-HTTP
external backends (e.g. an OpenShift Route without edge TLS).

### 5. YAML Preview

View and edit all generated files in a code viewer inside the browser.

### 6. Validation

Automated checks on generated YAML:
- ✔ YAML syntax
- ✔ CRD (API version)
- ✔ Namespace configuration
- ✔ Resource reference consistency
- ✔ Secret placeholder detection

### 7. ZIP Download

Download all generated files as a ZIP archive.  
Example filename: `customer-api.zip`

### 8. ZIP Import / Apply Connectivity Link Config

Upload a ZIP and:
- Preview and edit YAML in the browser
- Bulk-replace the target Namespace
- Apply directly to the cluster via `oc apply` (through backend)

Automatic processing on apply:
- Auto-create `migration-tool-istio-manager` Role and RoleBinding in the target Namespace
- Grants permissions for Istio / Kuadrant / Gateway API / core API resources
- Automatic `apiVersion` normalization (e.g., `kuadrant.io/v1beta2 → v1`)
- Exports each applied resource from the cluster and saves to history

Test command feature:
- Generates a curl command for connectivity verification after apply
- Custom path input field to append a path to the base URL

### 9. Conversion History (ZIP Import)

A history record is created for every ZIP Import run:

| Field | Description |
|-------|-------------|
| Timestamp | Execution date/time |
| Type | ZIP Import / Convert |
| Namespace | Target Namespace |
| Status | COMPLETED / PARTIAL / FAILED |
| Success count | Number of successfully applied resources |
| Failure count | Number of failed resources |
| Error details | Filename, Kind, Name, error message per failure (expandable row) |
| YAML download | ZIP of resources exported from cluster after apply |

Actions:
- Checkbox selection for bulk delete (to reduce DB size)
- Per-row ZIP download button to retrieve applied YAML

### 10. Gateway Info

Retrieves Gateway resource list and Listener information from the cluster in real time.

### 11. Namespace Setup

Automatically applies resources required for Connectivity Link to operate in the target Namespace.

---

## Directory Structure

```
migration-toolkit-rhcl/
├── backend/                    # Quarkus backend (Java 21)
│   └── src/main/java/com/redhat/migrationtoolkit/rhcl/
│       ├── controller/         # REST endpoints
│       │   ├── ConnectionController.java   # 3scale connection test
│       │   ├── ExportController.java       # Service list & compatibility check
│       │   ├── ConversionController.java   # YAML generation
│       │   ├── ValidationController.java   # YAML validation
│       │   ├── PackageController.java      # ZIP download
│       │   ├── ApplyController.java        # Cluster apply, history save, auto RBAC
│       │   ├── ImportController.java       # ZIP upload & parsing
│       │   ├── HistoryController.java      # History CRUD, ZIP download, bulk delete
│       │   ├── GatewayInfoController.java  # Gateway resource info
│       │   ├── ClusterController.java      # Cluster base domain auto-detection
│       │   └── SetupController.java        # Namespace setup
│       ├── service/
│       │   └── ConversionService.java  # 3scale → Connectivity Link YAML generation
│       ├── entity/             # Panache entities (JPA)
│       │   ├── ProjectEntity.java
│       │   └── ConversionHistoryEntity.java
│       ├── dto/                # Request/response DTOs
│       ├── model/              # Domain models
│       ├── client/             # 3scale API client
│       ├── util/
│       │   └── Messages.java   # i18n ResourceBundle wrapper (Accept-Language aware, default: English)
│       └── resources/
│           ├── application.properties
│           ├── db/migration/
│           │   ├── V1__init.sql
│           │   ├── V2__add_sequences.sql
│           │   └── V3__import_history.sql
│           ├── messages_ja.properties
│           └── messages_en.properties
├── frontend/                   # React + PatternFly frontend
│   └── src/
│       ├── pages/              # Page components
│       │   ├── ConnectionPage.tsx
│       │   ├── APISelectionPage.tsx
│       │   ├── CompatibilityPage.tsx
│       │   ├── ConversionPage.tsx
│       │   ├── YAMLViewerPage.tsx
│       │   ├── ValidationPage.tsx
│       │   ├── DownloadPage.tsx
│       │   ├── ImportPage.tsx      # ZIP Import, apply, curl test with custom path
│       │   └── HistoryPage.tsx     # History list, bulk delete, ZIP download
│       ├── api/
│       │   ├── client.ts       # Axios API client
│       │   └── types.ts        # TypeScript type definitions
│       ├── locales/
│       │   ├── ja.json         # Japanese UI strings
│       │   └── en.json         # English UI strings
│       ├── i18n.ts             # react-i18next configuration
│       └── App.tsx             # Layout & routing
├── deploy/                     # OpenShift provisioning
│   ├── install.sh              # All-in-one install script (bilingual)
│   ├── backend/                # Backend OpenShift resource YAMLs
│   ├── frontend/               # Frontend OpenShift resource YAMLs
│   └── postgres/               # PostgreSQL Operator / Cluster / SCC YAMLs
└── README.md
```

---

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/connection/test` | Test 3scale connection |
| GET | `/api/services` | List API services |
| GET | `/api/services/{id}` | Get service details |
| GET | `/api/services/{id}/compatibility` | Run compatibility check |
| POST | `/api/convert` | Generate YAML (run conversion) |
| POST | `/api/validate` | Validate generated YAML |
| POST | `/api/download/zip` | Download ZIP |
| POST | `/api/apply` | Apply to cluster (Server-Side Apply, auto RBAC, history save) |
| POST | `/api/import/zip` | Upload and parse ZIP |
| GET | `/api/history` | List conversion history (lightweight, excludes exportedYaml) |
| GET | `/api/history/{id}` | Get conversion history entry |
| GET | `/api/history/{id}/download` | Download applied YAML as ZIP |
| DELETE | `/api/history` | Bulk delete history entries by ID list |
| GET | `/api/history/projects` | List projects |
| GET | `/api/gateway/info` | Get Gateway resource info from cluster |
| GET | `/api/cluster/domain` | Auto-detect the cluster's base domain from the backend's own Route (used by "Auto-detect URL") |
| POST | `/api/setup/namespace` | Apply Namespace setup resources |
| GET | `/api/setup/status` | Check Namespace setup status |

Swagger UI: `https://<backend-route>/q/swagger-ui`

---

## Data Model

```
Project
  ├── id (PK)
  ├── name
  ├── namespace
  ├── threescaleUrl
  └── createdAt

ConversionHistory
  ├── id (PK)
  ├── source          CONVERT | IMPORT
  ├── namespace       target Namespace
  ├── serviceId       (CONVERT only)
  ├── serviceName     (CONVERT only)
  ├── status          COMPLETED | PARTIAL | FAILED
  ├── compatibilityScore  (CONVERT only)
  ├── totalCount      total resources attempted
  ├── successCount    successfully applied resources
  ├── failureCount    failed resources
  ├── failureDetails  JSON: [{fileName, kind, name, error}]
  ├── exportedYaml    JSON: {filename → yaml} exported from cluster after apply
  ├── yamlContent     (CONVERT only: full generated YAML text)
  └── createdAt
```

Flyway migrations:
- `V1__init.sql` — initial schema (Project / ConversionHistory)
- `V2__add_sequences.sql` — sequence additions
- `V3__import_history.sql` — Import history fields (source / namespace / totalCount / etc.)

---

## Internationalization (i18n)

### Frontend

- Powered by `react-i18next`
- Default language: **English (en)**
- Strings managed in `frontend/src/locales/ja.json` / `en.json`
- Runtime language switch via the **JA / EN** toggle in the masthead

### Backend

- Java standard `ResourceBundle`
- `backend/src/main/resources/messages_ja.properties` / `messages_en.properties`
- `Messages` bean (`@ApplicationScoped`) injected into controllers
- Default locale: **English**. Controllers that return user-facing messages
  (`ApplyController`, `ImportController`) resolve the locale from the request's
  `Accept-Language` header via `Messages.resolveLocale(...)`, falling back to
  English for anything other than `ja`.
- The frontend's Axios client (`frontend/src/api/client.ts`) sends the current
  `i18n.language` as `Accept-Language` on every request, so backend response
  messages (e.g. `apply.success`) always match the UI language.

## Testing

Run these from `backend/` unless noted.

### Run all tools + generate HTML report

```bash
./generate-report.sh
```

### To include Wapiti (DAST)

```bash
./generate-report.sh --with-wapiti --wapiti-url http://your-app:8080
```

### Maven only (Checkstyle + PMD + JaCoCo + Unit Test)

```bash
mvn verify checkstyle:checkstyle pmd:pmd
```

See also [CONTRIBUTING.md](CONTRIBUTING.md) for frontend tests and local setup. The SPA API base URL is bake-time `VITE_API_URL` (not a runtime `REACT_APP_*` env).

---

## Maintainer: make this repository operational (post-merge)

This section is for the repository owner after merging the **Simplify Tool Installation** contribution (Helm chart, Dockerfiles, Quay CI/CD). Application source under `backend/src` / `frontend/src` is unchanged; this runbook activates packaging, Pages, and CI.

### Phase 0 — Already available after merge (no action)

- Dockerfiles, GitHub Actions workflows, Helm chart, and `docs/` with chart `0.1.0` packaged
- Public images ready to install:
  - [`quay.io/everythingascode/migration-toolkit-rhcl-backend`](https://quay.io/repository/everythingascode/migration-toolkit-rhcl-backend) (`latest`, `v0.1.0`)
  - [`quay.io/everythingascode/migration-toolkit-rhcl-frontend`](https://quay.io/repository/everythingascode/migration-toolkit-rhcl-frontend) (`latest`, `v0.1.0`)
- Integration validated on [`Everything-is-Code/migration-toolkit-rhcl`](https://github.com/Everything-is-Code/migration-toolkit-rhcl) with [from-3scale-to-connectivity-link](https://github.com/Everything-is-Code/from-3scale-to-connectivity-link)
- **Gaps until you finish this runbook:** upstream CI cannot push to Quay without secrets; upstream `helm repo add` needs GitHub Pages on `/docs`

### Phase 1 — Publish GitHub Pages from `/docs` (Helm chart repository)

Required for `helm repo add` against this repository’s Pages URL.

1. Open the repo → **Settings → Pages**
2. **Build and deployment → Source:** Deploy from a branch
3. **Branch:** `main` · **Folder:** `/docs` → **Save**
4. On `main`, confirm these files exist: `docs/index.yaml`, `docs/migration-toolkit-rhcl-0.1.0.tgz`, `docs/.nojekyll`, `docs/index.html`
5. Wait until Pages status is **built** (Settings → Pages shows the site URL)
6. Smoke-test HTTP:
   - `https://everything-is-code.github.io/migration-toolkit-rhcl/` → 200
   - `https://everything-is-code.github.io/migration-toolkit-rhcl/index.yaml` → 200 and lists the chart
   - `https://everything-is-code.github.io/migration-toolkit-rhcl/migration-toolkit-rhcl-0.1.0.tgz` → 200
7. In a follow-up commit, update [`scripts/lib/helm-repo-url.sh`](scripts/lib/helm-repo-url.sh) to `https://everything-is-code.github.io/migration-toolkit-rhcl/` and align the `helm repo add` example in this README
8. **Temporary bridge:** until upstream Pages is live, users can install from `https://everything-is-code.github.io/migration-toolkit-rhcl/`
9. **Done when:** `helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/ && helm search repo migration-toolkit-rhcl` shows `0.1.0`

### Phase 2 — Red Hat Container Registry secrets (required for CI image builds)

CI Dockerfiles pull `registry.access.redhat.com/ubi9/...`. The chart PostgreSQL image uses `registry.redhat.io/rhel9/postgresql-15`. Without registry login, **Build and push to Quay.io** fails on base-image pull.

1. Open [Red Hat Terms-based Registry / Token Generation](https://access.redhat.com/terms-based-registry/)
2. Sign in with your Red Hat account
3. Create or open a **Registry Service Account**
4. Copy the service account **username** and **token** (password)
5. GitHub → **Settings → Secrets and variables → Actions → New repository secret**:
   - `REDHAT_REGISTRY_USERNAME` = service account username
   - `REDHAT_REGISTRY_PASSWORD` = service account token
6. **Done when:** both secrets appear in the Actions secrets list (values hidden)

### Phase 3 — Quay.io secrets (required for CI to publish images)

Workflows default to `QUAY_NAMESPACE: everythingascode` and Helm values already point at those public images.

**Path A — Day-0 operational (recommended):** keep using the published `everythingascode` images. Helm install works **without** your own Quay push. Configure `QUAY_USERNAME` / `QUAY_PASSWORD` only if you have write access to that Quay namespace. Otherwise leave CI push disabled until Path B.

**Path B — Maintainer-owned Quay (fully autonomous):**

1. On [quay.io](https://quay.io) create repositories `migration-toolkit-rhcl-backend` and `migration-toolkit-rhcl-frontend` (public, or private + cluster pull secret)
2. Create a **Robot Account** with write on both repos; copy robot username + token
3. Add GitHub secrets `QUAY_USERNAME` and `QUAY_PASSWORD`
4. Set `QUAY_NAMESPACE` in [`.github/workflows/build-push-quay.yml`](.github/workflows/build-push-quay.yml) and [`.github/workflows/release.yml`](.github/workflows/release.yml)
5. Update `backend.image.repository` / `frontend.image.repository` in [`helm/migration-toolkit-rhcl/values.yaml`](helm/migration-toolkit-rhcl/values.yaml) and image annotations in [`helm/migration-toolkit-rhcl/Chart.yaml`](helm/migration-toolkit-rhcl/Chart.yaml)
6. Bump chart version/tags if needed and republish `docs/` (release workflow or `helm package` + `helm repo index`)
7. **Done when:** **Build and push to Quay.io** (`workflow_dispatch`) is green and tags appear on your Quay repos

| Secret | Needed for | Source |
|--------|------------|--------|
| `REDHAT_REGISTRY_USERNAME` | CI pull of UBI / RH bases | [terms-based-registry](https://access.redhat.com/terms-based-registry/) service account |
| `REDHAT_REGISTRY_PASSWORD` | CI pull | Same portal (token) |
| `QUAY_USERNAME` | CI push to Quay | Quay robot account |
| `QUAY_PASSWORD` | CI push to Quay | Quay robot token |

**Troubleshooting — `Build and push to Quay.io` fails with `401 Unauthorized`**

The Docker **build** usually succeeds; the failure is on **push** to `quay.io/everythingascode/...`.

1. Confirm secrets exist: **Settings → Secrets and variables → Actions** (`QUAY_USERNAME`, `QUAY_PASSWORD`).
2. Robot username format is `everythingascode+<robot-name>` (not your personal Quay login).
3. Rotate the robot token on [quay.io](https://quay.io) → Organization **everythingascode** → Robot Accounts → regenerate token → update `QUAY_PASSWORD`.
4. Ensure the robot has **Write** on `migration-toolkit-rhcl-backend` and `migration-toolkit-rhcl-frontend`.
5. Re-run **Build and push to Quay.io** (workflow runs a pre-flight check via `scripts/ci/verify-quay-credentials.sh`).

If you do not have write access to the `everythingascode` namespace, use **Path A** (public images already published) and skip Quay push until you own a namespace (Path B).

### Phase 4 — Enable and verify GitHub Actions

1. **Settings → Actions → General:** allow Actions / workflows if restricted
2. **Actions** → **Build and push to Quay.io** → **Run workflow** on `main`
3. RH login failures → Phase 3; Quay login/push failures → Phase 4
4. Commits whose message contains `[skip build]` skip the push job
5. **Done when:** the latest run is green, or you consciously stay on Path A without push

### Phase 5 — Helm install smoke test (cluster)

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
# If upstream Pages is not ready yet:
# helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
oc -n migration-toolkit get pods,route
```

Confirm postgres/backend/frontend Ready, frontend Route opens the UI, and `/api/*` reaches the backend via nginx.

GitOps: apply [`examples/gitops/application.yaml`](examples/gitops/application.yaml) with `repoURL` set to this upstream repo.

**Done when:** the Route works and a basic migration UI flow is usable.

### Phase 6 — Semver releases (optional)

1. Bump `version` / `appVersion` in `Chart.yaml` (and image tags in values if needed)
2. Push git tag `vX.Y.Z` matching `Chart.yaml` version exactly (`release.yml` enforces this)
3. Release workflow rebuilds/pushes images, packages the chart into `docs/`, opens PR `chore/helm-chart-vX.Y.Z`, creates a GitHub Release
4. Merge that PR so Pages serves the new chart version

### Phase 7 — Operational checklist

- [ ] GitHub Pages `main` + `/docs` **built**; `index.yaml` returns 200
- [ ] `helm-repo-url.sh` + README point at upstream Pages
- [ ] Secrets `REDHAT_REGISTRY_*` configured
- [ ] Secrets `QUAY_*` configured **or** documented Path A (use existing public images)
- [ ] Quay workflow green (if Path B / push)
- [ ] `helm install` smoke OK on a cluster
- [ ] (Optional) GitOps Application syncs from upstream

---

*Maintained by Noriaki Mushino — nmushino@redhat.com*

---

---

# 日本語ドキュメント

## Migration Toolkit for Red Hat Connectivity Link

3scale から Red Hat Connectivity Link へ移行するための GUI ツールキットです。  
Quarkus バックエンド + React/PatternFly フロントエンド で構成されています。

---

## 目次

- [スクリーンショット](#スクリーンショット)
- [前提条件・必要ツール](#前提条件必要ツール)
- [クイックスタート](#クイックスタート)
  - [Helm でのインストール](#helm-でのインストールany-cluster)
- [アーキテクチャ](#アーキテクチャ)
- [処理フロー](#処理フロー)
- [機能一覧](#機能一覧)
- [ディレクトリ構成](#ディレクトリ構成)
- [API一覧](#api一覧)
- [データモデル](#データモデル)
- [国際化対応 (i18n)](#国際化対応-i18n)

---

## スクリーンショット

| 3scale 接続設定 | 互換性チェック |
|:-:|:-:|
| ![3scale 接続設定](.claude/images/connect.png) | ![互換性チェック](.claude/images/apilist.png) |

| YAML 生成 | YAML プレビュー |
|:-:|:-:|
| ![YAML 生成](.claude/images/yaml.png) | ![YAML プレビュー](.claude/images/preview.png) |

| バリデーション | ダウンロード |
|:-:|:-:|
| ![バリデーション](.claude/images/validation.png) | ![ダウンロード](.claude/images/download.png) |

| ZIP インポート / CL 設定 | curl 疎通テスト |
|:-:|:-:|
| ![ZIP インポート](.claude/images/import.png) | ![curl 疎通テスト](.claude/images/rhcltest.png) |

---

## 前提条件・必要ツール

下記ツールの利用が前提となっています
https://github.com/Everything-is-Code/from-3scale-to-connectivity-link

### ローカル開発環境

| ツール | バージョン | 用途 |
|--------|------------|------|
| Java (OpenJDK) | 21 以上 | バックエンドビルド |
| Apache Maven | 3.9.x 以上 | バックエンドビルド |
| Node.js | 22 | フロントエンドビルド |
| npm | 9 以上 | フロントエンド依存関係管理 |
| Docker / Podman | 最新版 | コンテナイメージビルド（ローカル検証時） |

### OpenShift クラスター

| ツール / コンポーネント | バージョン | 用途 |
|------------------------|------------|------|
| OpenShift Container Platform | 4.14 以上 | デプロイ先クラスター |
| `oc` CLI | クラスターに対応したバージョン | クラスター操作 |
| CrunchyData PostgreSQL Operator | 最新版 | データベース管理（OperatorHub から事前インストール） |
| Sail Operator (Istio) | 最新版 | Gateway API 実装 — `install.sh` が自動インストール |
| Red Hat Connectivity Link (`rhcl-operator`) | 最新版 | 移行対象コンポーネント — `install.sh` が自動インストール |

> **注意**: CrunchyData PostgreSQL Operator は `openshift-operators` Namespace へ  
> 事前にインストールしてください。インストールスクリプトが自動検出します。
>
> Sail Operator と Red Hat Connectivity Link（`rhcl-operator`、Red Hat Operators カタログ）は
> `install.sh` / `install.sh --kuadrant-only` が自動でインストールします。Community 版の
> `kuadrant-operator` ではなく、製品版の `rhcl-operator` を使用してください。Community 版には
> DevPortal CRD（`APIKey` / `APIProduct`）が含まれておらず、`AuthPolicy` も `v1beta2` までしか
> 提供されません（`v1` が必要です）。

### 3scale 環境

- 3scale Admin Portal への接続 URL と Personal Access Token

---

## クイックスタート

### Helm でのインストール（any cluster）

公開済みコンテナイメージを Helm で導入する推奨手順です（詳細は英語版 [Install with Helm](#install-with-helm-any-cluster) およびメンテナ向け [post-merge runbook](#maintainer-make-this-repository-operational-post-merge) を参照）。

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

変換アダプタ: [from-3scale-to-connectivity-link](https://github.com/Everything-is-Code/from-3scale-to-connectivity-link)

### OpenShift へのフルデプロイ

```bash
# 任意の Namespace を指定してインストール（デフォルト: migration-toolkit）
NAMESPACE=migration-toolkit ./deploy/install.sh

# バックエンドのみデプロイ
NAMESPACE=migration-toolkit ./deploy/install.sh --backend-only

# フロントエンドのみデプロイ
NAMESPACE=migration-toolkit ./deploy/install.sh --frontend-only

# DB のみセットアップ
NAMESPACE=migration-toolkit ./deploy/install.sh --db-only
```

インストールスクリプトが以下を自動処理します:

1. Namespace 作成
2. Sail Operator (Istio) + Red Hat Connectivity Link (`rhcl-operator`) のインストールと CRD 準備待機
3. CrunchyData PostgreSQL Operator インストール待機
4. PostgreSQL クラスター作成（SCC 含む）
5. バックエンド Maven ビルド → S2I → デプロイ
6. フロントエンド npm ビルド → S2I (nginx) → デプロイ
7. アクセス URL 表示

> **フロントエンドを手動で再ビルドする場合**（`install.sh` を使わない場合）: `npm run build` は
> `frontend/build/` を毎回空にするため（`vite.config.ts` の `emptyOutDir`）、コピーしていた
> `nginx-default-cfg/` ディレクトリも削除されます。バイナリ S2I ビルド
> （`oc start-build ... --from-dir=frontend/build`）を実行する前に、必ず再コピーしてください。
> 忘れると `/api/` へのリバースプロキシ設定なしのイメージがデプロイされ、バックエンドへの
> 全リクエストが 404 になります。
> ```bash
> mkdir -p frontend/build/nginx-default-cfg
> cp frontend/nginx-default-cfg/api-proxy.conf frontend/build/nginx-default-cfg/api-proxy.conf
> ```
> `install.sh` の `deploy_frontend` はこの処理を自動的に行います。

### 言語切替

```bash
# 英語（デフォルト）
./deploy/install.sh

# 日本語で実行
INSTALL_LANG=ja ./deploy/install.sh
```

> `install.sh` はシステムロケール（`$LANG`）を参照しなくなりました。デフォルトは英語で、
> 日本語に切り替えたい場合は明示的に `INSTALL_LANG=ja` を指定してください。

### ローカル開発

```bash
# バックエンド起動（PostgreSQL が localhost:5432 で起動していること）
cd backend
mvn quarkus:dev

# フロントエンド起動（別ターミナル）
cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

CORS のデフォルト許可オリジンは `http://localhost:5173`、`http://localhost:3000`、
`http://localhost:8080` です。OpenShift や一時デモでは `CORS_ORIGINS` または
`QUARKUS_HTTP_CORS_ORIGINS`（カンマ区切り）で上書きしてください。
エクスポート API のトークンは `Authorization: Bearer <token>` で送り、
クエリの `accessToken` / `access_token` は使いません。既存の POST ボディの
`accessToken` はそのまま利用できます。

---

## アーキテクチャ

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
        | ③ Converter (YAML Generator)       |
        | ④ Validation                       |
        | ⑤ Package Download (ZIP)           |
        | ⑥ Import / Apply to Cluster        |
        |    (RBAC 自動プロビジョニング含む)  |
        | ⑦ ZIP Import 変換履歴              |
        | ⑧ Gateway 情報取得                 |
        | ⑨ Namespace セットアップ           |
        +-----------------+------------------+
                    |               |
       from-3scale-to-connectivity  PostgreSQL
            -link (Adapter)         (CrunchyData)
                    |
          Connectivity Link YAML
         (Gateway / HTTPRoute / AuthPolicy /
          RateLimitPolicy / DestinationRule /
          ServiceEntry / Secret / ConfigMap)
```

**主要技術スタック**

| レイヤー | 技術 |
|---------|------|
| フロントエンド | React 18, PatternFly 5, Vite, TypeScript, react-i18next |
| バックエンド | Quarkus 3.27.5.1 (Java 21), RESTEasy Reactive, Hibernate ORM Panache |
| データベース | PostgreSQL (CrunchyData Operator 管理) |
| Kubernetes クライアント | Fabric8 Kubernetes Client 6.7.x |
| OpenAPI | SmallRye OpenAPI + Swagger UI (`/q/swagger-ui`) |
| マイグレーション | Flyway (V1〜V3) |
| デプロイ | OpenShift S2I, nginx (フロントエンド静的配信) |

---

## 処理フロー

```
① 3scale 接続設定 (URL / Access Token / Tenant / Namespace 入力)
      ↓
② API 一覧取得 (Service / Backend / MappingRule / Metrics / Policies / Authentication)
      ↓
③ 変換対象選択
      ↓
④ Compatibility Check (スコアリング: JWT / Rewrite / Lua Policy / SOAP など)
      ↓
⑤ YAML 生成 (from-3scale-to-connectivity-link アダプタ経由)
      ↓
⑥ YAML プレビュー / 編集
      ↓
⑦ Validation (YAML 構文 / CRD / Namespace / Secret / Reference 整合性)
      ↓
⑧ ZIP ダウンロード
      ↓
⑨ ZIP Import → Connectivity Link 画面から適用
     ・対象 Namespace に Role/RoleBinding を自動作成
     ・Server-Side Apply でクラスターへ適用
     ・適用後リソースを -o yaml 相当でエクスポート保存
      ↓
⑩ 変換履歴確認 (成功/失敗件数・エラー詳細・適用済み YAML の ZIP 再ダウンロード)
```

---

## 機能一覧

### 1. 3scale 接続設定

画面入力項目:
- 3scale Admin Portal URL
- Personal Access Token
- Tenant 名（オプション）
- 対象 OpenShift Namespace

バックエンドが呼び出す 3scale API:
```
GET /admin/api/services.json
GET /admin/api/backends.json
GET /admin/api/proxy_configs
GET /admin/api/policies
```

### 2. API 一覧取得

取得情報:
- Service 基本情報 (ID / 名称 / デプロイオプション)
- Backend (Private Endpoint)
- Mapping Rules
- Metrics
- Policies
- Authentication 方式 (API Key / OIDC / JWT など)

### 3. Compatibility Check

各 API ポリシー・機能を Connectivity Link でサポートできるか判定:

| 判定 | 意味 |
|------|------|
| ✔ SUPPORTED | そのまま移行可能 |
| ⚠ WARNING | 手動調整が必要 |
| × UNSUPPORTED | 非対応（要カスタム対応） |

Migration Score (0–100%) でトータルの移行難易度を数値化します。

### 4. YAML 生成

`from-3scale-to-connectivity-link` アダプタを経由して以下のリソースを生成:

```
{service-name}/
  gateway.yaml        # Kuadrant Gateway
  httproute.yaml      # HTTPRoute
  policy.yaml         # AuthPolicy / RateLimitPolicy
  tlspolicy.yaml      # Kuadrant TLSPolicy（オプトイン; cert-manager issuerRef）
  dnspolicy.yaml      # Kuadrant DNSPolicy（オプトイン; 任意の providerRefs）
  secret.yaml         # 認証情報 (REPLACE_ME プレースホルダー)
  configmap.yaml      # 設定値
  destinationrule.yaml # Istio DestinationRule (TLS 設定)
  serviceentry.yaml   # Istio ServiceEntry (外部サービス登録)
  README.md
```

> `secret.yaml` 内の `REPLACE_ME` は手動で実際の値に置き換えてください。

**TLSPolicy（オプトイン）**: Conversion 画面で「TLSPolicy を生成」を有効にすると、
`{name}-gateway` を対象とし cert-manager の `issuerRef`（UI 初期値: `ClusterIssuer` /
`letsencrypt-prod`）を持つ `tlspolicy.yaml` がパッケージに含まれます。Gateway の https
listener は引き続き Secret `{name}-tls` を参照し、適用後に cert-manager がその Secret を作成します。
生の Certificate CR は生成しません。ClusterIssuer はクラスタ上に事前に存在する必要があります。

**DNSPolicy（オプトイン）**: 「DNSPolicy と Gateway hostname を生成」を有効にすると、Gateway の
`http` / `https` 両 listener に `hostname: {dnsHostname}` が設定されます（UI 初期値は
`GET /api/cluster/domain` から `{kebabName}.{clusterDomain}` — domain には既に `apps.` が
含まれるため二重に付けません）。パッケージには `{name}-gateway` を対象とする
`dnspolicy.yaml` が含まれます。DNS provider Secret 名を指定すると `providerRefs: [{ name }]`
を含め、空なら `providerRefs` を省略してクラスタの `kuadrant.io/default-provider=true`
Secret を使います。provider の認証情報はパッケージに書き込みません。

**外部バックエンドの自動判定**: 「バックエンドは外部サービス」チェックボックスをオフのままでも、
バックエンドタイプの判定はチェックボックスの値だけでなく、3scale に登録済みのバックエンド URL
（`service.backends[0].privateEndpoint`）へ自動フォールバックするようになりました。これにより
`httproute.yaml` / `serviceentry.yaml` / `configmap.yaml` の内容が矛盾しなくなります。また、
バックエンドのポートと `DestinationRule` の TLS 発信有無は、この URL のスキームから決定されます
（`http://` → ポート 80・TLS なし / `https://` → ポート 443・TLS `SIMPLE`）。以前は常に `443` +
TLS 固定だったため、TLS 未設定のプレーン HTTP な外部バックエンド（例: edge TLS 未設定の
OpenShift Route）に接続できませんでした。

### 5. YAML プレビュー

生成した全ファイルをブラウザ上でコードビューア形式で確認・編集できます。

### 6. Validation

生成 YAML に対して以下を自動チェック:
- ✔ YAML 構文チェック
- ✔ CRD (API バージョン) チェック
- ✔ Namespace 設定チェック
- ✔ リソース参照整合性チェック
- ✔ Secret プレースホルダーチェック

### 7. ZIP ダウンロード

全ファイルを ZIP アーカイブとしてダウンロード。  
ファイル名例: `customer-api.zip`

### 8. ZIP Import / Connectivity Link 設定適用

アップロードした ZIP の YAML を:
- ブラウザ上でプレビュー・編集
- Namespace を一括置換
- `oc apply` コマンドでクラスターへ直接適用（バックエンド経由）

適用時の自動処理:
- 対象 Namespace に `migration-tool-istio-manager` Role と RoleBinding を自動作成（RBAC 自動プロビジョニング）
- Istio / Kuadrant / Gateway API / コア API リソースへの権限を付与
- `apiVersion` の自動正規化（`kuadrant.io/v1beta2 → v1` など）
- 適用後に各リソースをクラスターからエクスポートし履歴に保存

テストコマンド機能:
- 適用後の疎通確認用 curl コマンドを自動生成
- 追加パス入力欄でベース URL にパスを付与可能

### 9. 変換履歴（ZIP Import 専用）

ZIP Import を実行するたびに 1 件の履歴レコードを作成:

| 項目 | 内容 |
|------|------|
| 実行日時 | タイムスタンプ |
| 種別 | ZIP Import / Convert |
| Namespace | 適用先 Namespace |
| ステータス | COMPLETED / PARTIAL / FAILED |
| 成功件数 | 正常適用されたリソース数 |
| 失敗件数 | エラーになったリソース数 |
| エラー詳細 | 失敗リソースのファイル名・Kind・Name・エラーメッセージ（展開表示） |
| YAML ダウンロード | 適用済みリソースを `-o yaml` 相当でエクスポートした ZIP |

操作:
- チェックボックスで複数選択して一括削除（DB サイズ削減目的）
- 各行の ZIP ダウンロードボタンで適用済み YAML を取得

### 10. Gateway 情報取得

クラスターの Gateway リソース一覧と Listener 情報をリアルタイムに取得。

### 11. Namespace セットアップ

対象 Namespace に Connectivity Link 動作に必要な初期リソースを自動適用。

---

## ディレクトリ構成

```
migration-toolkit-rhcl/
├── backend/                    # Quarkus バックエンド (Java 21)
│   └── src/main/java/com/redhat/migrationtoolkit/rhcl/
│       ├── controller/         # REST エンドポイント
│       │   ├── ConnectionController.java   # 3scale 接続テスト
│       │   ├── ExportController.java       # サービス一覧・互換性チェック
│       │   ├── ConversionController.java   # YAML 生成
│       │   ├── ValidationController.java   # YAML バリデーション
│       │   ├── PackageController.java      # ZIP ダウンロード
│       │   ├── ApplyController.java        # クラスター適用・履歴保存・RBAC 自動作成
│       │   ├── ImportController.java       # ZIP アップロード・解析
│       │   ├── HistoryController.java      # 変換履歴 CRUD・ZIP ダウンロード
│       │   ├── GatewayInfoController.java  # Gateway リソース情報取得
│       │   ├── ClusterController.java      # クラスタードメイン自動検出
│       │   └── SetupController.java        # Namespace セットアップ
│       ├── service/
│       │   └── ConversionService.java  # 3scale → Connectivity Link YAML 生成ロジック
│       ├── entity/             # Panache エンティティ (JPA)
│       │   ├── ProjectEntity.java
│       │   └── ConversionHistoryEntity.java
│       ├── dto/                # リクエスト/レスポンス DTO
│       ├── model/              # ドメインモデル
│       ├── client/             # 3scale API クライアント
│       ├── util/
│       │   └── Messages.java   # i18n ResourceBundle ラッパー（Accept-Language 対応、デフォルト: 英語）
│       └── resources/
│           ├── application.properties
│           ├── db/migration/
│           │   ├── V1__init.sql            # 初期スキーマ
│           │   ├── V2__add_sequences.sql   # シーケンス追加
│           │   └── V3__import_history.sql  # Import 履歴フィールド追加
│           ├── messages_ja.properties      # バックエンド日本語メッセージ
│           └── messages_en.properties      # バックエンド英語メッセージ
├── frontend/                   # React + PatternFly フロントエンド
│   └── src/
│       ├── pages/              # 各画面コンポーネント
│       │   ├── ConnectionPage.tsx      # 3scale 接続設定
│       │   ├── APISelectionPage.tsx    # API 一覧・選択
│       │   ├── CompatibilityPage.tsx   # 互換性チェック結果
│       │   ├── ConversionPage.tsx      # YAML 生成実行
│       │   ├── YAMLViewerPage.tsx      # YAML プレビュー・編集
│       │   ├── ValidationPage.tsx      # バリデーション結果
│       │   ├── DownloadPage.tsx        # ZIP ダウンロード
│       │   ├── ImportPage.tsx          # ZIP Import・クラスター適用・curl テスト
│       │   └── HistoryPage.tsx         # 変換履歴一覧・削除・ZIP 再ダウンロード
│       ├── api/
│       │   ├── client.ts       # Axios API クライアント
│       │   └── types.ts        # TypeScript 型定義
│       ├── locales/
│       │   ├── ja.json         # 日本語 UI 文字列
│       │   └── en.json         # 英語 UI 文字列
│       ├── i18n.ts             # react-i18next 設定
│       └── App.tsx             # レイアウト・ルーティング
├── deploy/                     # OpenShift プロビジョニング
│   ├── install.sh              # 一括インストールスクリプト（日英対応）
│   ├── backend/                # Backend OpenShift リソース YAML
│   ├── frontend/               # Frontend OpenShift リソース YAML
│   └── postgres/               # PostgreSQL Operator / Cluster / SCC YAML
└── README.md
```

---

## API 一覧

| Method | Path | 説明 |
|--------|------|------|
| POST | `/api/connection/test` | 3scale 接続テスト |
| GET | `/api/services` | API サービス一覧取得 |
| GET | `/api/services/{id}` | サービス詳細取得 |
| GET | `/api/services/{id}/compatibility` | 互換性チェック |
| POST | `/api/convert` | YAML 生成（変換実行） |
| POST | `/api/validate` | YAML バリデーション |
| POST | `/api/download/zip` | ZIP ダウンロード |
| POST | `/api/apply` | クラスターへ適用（Server-Side Apply・RBAC 自動作成・履歴保存） |
| POST | `/api/import/zip` | ZIP アップロード・解析 |
| GET | `/api/history` | 変換履歴一覧（`exportedYaml` 除く軽量レスポンス） |
| GET | `/api/history/{id}` | 変換履歴詳細 |
| GET | `/api/history/{id}/download` | 変換履歴の適用済み YAML を ZIP ダウンロード |
| DELETE | `/api/history` | 変換履歴の一括削除（ID リスト指定） |
| GET | `/api/history/projects` | プロジェクト一覧 |
| GET | `/api/gateway/info` | Gateway リソース情報取得 |
| GET | `/api/cluster/domain` | バックエンド自身の Route からクラスタードメインを自動検出（「Auto-detect URL」で使用） |
| POST | `/api/setup/namespace` | Namespace セットアップ |
| GET | `/api/setup/status` | Namespace セットアップ状態確認 |

Swagger UI: `https://<backend-route>/q/swagger-ui`

---

## データモデル

```
Project
  ├── id (PK)
  ├── name
  ├── namespace
  ├── threescaleUrl
  └── createdAt

ConversionHistory
  ├── id (PK)
  ├── source          CONVERT | IMPORT
  ├── namespace       適用先 Namespace
  ├── serviceId       (変換時のみ)
  ├── serviceName     (変換時のみ)
  ├── status          COMPLETED | PARTIAL | FAILED
  ├── compatibilityScore  (変換時のみ)
  ├── totalCount      適用試行リソース数
  ├── successCount    成功リソース数
  ├── failureCount    失敗リソース数
  ├── failureDetails  JSON: [{fileName, kind, name, error}]
  ├── exportedYaml    JSON: {filename → yaml} クラスターからエクスポートした YAML
  ├── yamlContent     (変換時のみ: 生成 YAML 全文)
  └── createdAt
```

Flyway マイグレーション:
- `V1__init.sql` — 初期スキーマ（Project / ConversionHistory）
- `V2__add_sequences.sql` — シーケンス追加
- `V3__import_history.sql` — Import 履歴フィールド追加（source / namespace / totalCount など）

---

## 国際化対応 (i18n)

### フロントエンド

- `react-i18next` を使用
- デフォルト言語: **英語 (en)**
- `frontend/src/locales/ja.json` / `en.json` で文字列管理
- マストヘッド右端の **JA / EN** タブで実行時に切り替え可能

### バックエンド

- Java 標準 `ResourceBundle` を使用
- `backend/src/main/resources/messages_ja.properties` / `messages_en.properties`
- `Messages` Bean (`@ApplicationScoped`) が各コントローラに注入される
- デフォルトロケール: **英語**。ユーザー向けメッセージを返すコントローラ
  （`ApplyController`、`ImportController`）は、リクエストの `Accept-Language` ヘッダーから
  `Messages.resolveLocale(...)` でロケールを解決し、`ja` 以外はすべて英語にフォールバックします。
- フロントエンドの Axios クライアント（`frontend/src/api/client.ts`）が、リクエストごとに現在の
  `i18n.language` を `Accept-Language` として送信するため、バックエンドの応答メッセージ
  （例: `apply.success`）は常に UI の表示言語と一致します。

## テスト

特に記載がない限り `backend/` で実行します。

### 全ツール実行 + HTML レポート生成

```bash
./generate-report.sh
```

### Wapiti (DAST) も含める場合

```bash
./generate-report.sh --with-wapiti --wapiti-url http://your-app:8080
```

### Maven のみ (Checkstyle + PMD + JaCoCo + Unit Test)

```bash
mvn verify checkstyle:checkstyle pmd:pmd
```

ローカルセットアップとフロントエンドのテストは [CONTRIBUTING.md](CONTRIBUTING.md) を参照してください。SPA の API ベース URL はビルド時の `VITE_API_URL` です（ランタイムの `REACT_APP_*` ではありません）。