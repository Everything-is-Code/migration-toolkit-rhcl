# Design: Quay CI/CD + Helm chart (apishift-slim)

**Date:** 2026-07-16  
**Status:** Approved  
**Repo (fork):** https://github.com/maximilianoPizarro/migration-toolkit-rhcl  
**Reference:** https://github.com/Everything-is-Code/apishift  

## Goal

Add container build/push pipelines to Quay.io and a Helm chart that centralizes OpenShift manifests and release packaging, following the apishift pattern but scoped to what this toolkit actually needs. This fork is a collaboration prototype; **mushino** is the sole Helm chart maintainer and will replicate the approach upstream.

## Decisions (locked)

| Topic | Choice |
|--------|--------|
| Approach | Apishift-slim (CI + Helm + docs Helm repo; no AI/DataGrid/observability) |
| Backend Quay | `quay.io/maximilianopizarro/migration-toolkit-rhcl-backend` |
| Frontend Quay | `quay.io/maximilianopizarro/migration-toolkit-rhcl-frontend` |
| PostgreSQL | Embedded UBI Deployment (`registry.redhat.io/rhel9/postgresql-15`), like apishift |
| Helm publish | `helm package` → `docs/` + `index.yaml` + automated PR + GitHub Release |
| Chart maintainer | **mushino only** |
| Legacy `deploy/` | Leave S2I manifests untouched |

## Architecture

```text
push main  →  build-push-quay.yml  →  Quay images (:latest, :vX.Y.Z, :sha)
tag v*     →  release.yml          →  Quay release tags + helm package → docs/ PR + GitHub Release
helm install → frontend Route → nginx /api → backend → Postgres
```

Secrets (already configured in the GitHub repo):

- `QUAY_USERNAME`, `QUAY_PASSWORD`
- `REDHAT_REGISTRY_USERNAME`, `REDHAT_REGISTRY_PASSWORD`

## Components

### 1. Dockerfiles

**`backend/Dockerfile.jvm`**

- Multi-stage from `registry.access.redhat.com/ubi9/openjdk-21` (builder) and `ubi9/openjdk-21-runtime`.
- `mvn package -DskipTests`; copy quarkus-app layers to `/deployments`.
- Expose `8080`; run as non-root (UID 185).
- No kuadrantctl in v1 (out of scope unless later required).

**`frontend/Dockerfile.ci`**

- Builder: Node (LTS) — `npm ci` + `npm run build` (Vite → `dist/`).
- Runtime: `registry.access.redhat.com/ubi9/nginx-124`.
- Ship static assets + base `nginx.conf`; Helm overlays backend upstream via ConfigMap for `/api/` proxy.

### 2. GitHub Actions

**`.github/workflows/build-push-quay.yml`**

- Triggers: `push` to `main`, `workflow_dispatch`.
- Skip if commit message contains `[skip build]`.
- Matrix:
  - `migration-toolkit-rhcl-backend` → `./backend`, `Dockerfile.jvm`
  - `migration-toolkit-rhcl-frontend` → `./frontend`, `Dockerfile.ci`
- Logins: Quay, `registry.access.redhat.com`, `registry.redhat.io`.
- Tags: `latest`, `v{Chart.version}`, `{github.sha}`.
- Buildx + GHA cache scoped per image.

**`.github/workflows/release.yml`**

- Triggers: push tags `v*`, `workflow_dispatch`.
- Validate git tag matches `Chart.yaml` version when triggered by tag push.
- Rebuild/push images with release tag + `latest`.
- `helm package helm/migration-toolkit-rhcl` → `docs/`.
- `helm repo index docs/ --url HELM_REPO_URL --merge docs/index.yaml`.
- Open PR `chore/helm-chart-vX.Y.Z` with packaged chart + index.
- Create/update GitHub Release for the tag.

### 3. Version / Helm repo scripts

- `scripts/lib/common.sh` — root path helpers.
- `scripts/lib/version.sh` — export `VERSION` / `VERSION_V` from `helm/migration-toolkit-rhcl/Chart.yaml`.
- `scripts/lib/helm-repo-url.sh` — `HELM_REPO_URL=https://maximilianopizarro.github.io/migration-toolkit-rhcl/` (fork; mushino changes when replicating).

### 4. Helm chart `helm/migration-toolkit-rhcl`

**Chart.yaml**

- `name: migration-toolkit-rhcl`
- `version` / `appVersion`: `0.1.0` initially
- `maintainers`: only mushino
- Artifact Hub image annotations for backend, frontend, postgresql (Quay + registry.redhat.io, whitelisted)

**Templates**

| File | Purpose |
|------|---------|
| `_helpers.tpl` | name, labels, selectors |
| `backend-deployment.yaml` | Quarkus; DB env `DB_*` |
| `backend-service.yaml` | ClusterIP `:8080` |
| `frontend-deployment.yaml` | nginx frontend |
| `frontend-service.yaml` | ClusterIP `:8080` |
| `frontend-nginx-configmap.yaml` | `/api/` → backend service FQDN of the release |
| `postgresql.yaml` | Deployment + Service when enabled |
| `secret.yaml` | DB credentials |
| `serviceaccount.yaml` | backend SA |
| `clusterrole.yaml` | Same verbs/groups as `deploy/backend/06-rbac.yaml` |
| `route.yaml` | OpenShift Route → frontend, edge TLS |

**values.yaml (core)**

```yaml
backend:
  image:
    repository: quay.io/maximilianopizarro/migration-toolkit-rhcl-backend
    tag: v0.1.0
frontend:
  image:
    repository: quay.io/maximilianopizarro/migration-toolkit-rhcl-frontend
    tag: v0.1.0
postgresql:
  enabled: true
  username: migrationtool
  password: migrationtool
  # database name: migrationtool
route:
  enabled: true
  host: ""
  tls:
    termination: edge
rbac:
  clusterAdmin: false
```

Backend env mapping (must match `application.properties`):

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- When `postgresql.enabled: true`, host defaults to `{{ release }}-postgresql` (helper).
- When disabled, use `postgresql.url` / credentials from values or existing secret.

### 5. Docs Helm repository

- `docs/index.yaml` (created/updated by release workflow).
- Packaged `migration-toolkit-rhcl-*.tgz` under `docs/`.
- Requires GitHub Pages from `/docs` (or equivalent) for install via `helm repo add`.

## Out of scope

- AI / LiteLLM, DataGrid, ServiceMonitor, Grafana, Developer Hub, Argo CD discovery.
- Removing or rewriting `deploy/` S2I path.
- kuadrantctl inside the backend image (unless a later change requires it).
- Changing Quay ownership / mushino’s upstream image namespaces (his job when replicating).

## Error handling / ops notes

- Red Hat registry login is required for UBI and postgresql base pulls in CI.
- Release fails if `v*` tag ≠ `Chart.yaml` version (prevents mismatched chart/image tags).
- Frontend must not hardcode `migration-toolkit` namespace in the image; proxy target comes from Helm ConfigMap.

## Testing (acceptance)

1. Manual `workflow_dispatch` of `build-push-quay` publishes both images to Quay.
2. `docker pull` (or `podman pull`) of `:latest` succeeds for backend and frontend.
3. `helm lint helm/migration-toolkit-rhcl` passes.
4. Tag `v0.1.0` (after Chart.yaml is `0.1.0`) runs release: images tagged, PR opens with `docs/*.tgz` + `index.yaml`, GitHub Release exists.
5. On a cluster with Routes: `helm install` brings up postgres + backend + frontend; UI loads; `/api/` reaches backend health.

## Replication note for mushino

When copying to the canonical repo, update at minimum:

- Quay namespace / image names
- `HELM_REPO_URL` and Chart `home` / `sources`
- Keep `maintainers: mushino` as the sole maintainer entry
