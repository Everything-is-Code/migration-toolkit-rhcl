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
| GitOps (provisional) | Consume chart **from git path** (Argo CD / OpenShift GitOps); Pages Helm repo is secondary |

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

## GitOps consumption (provisional)

Day-1 install from **another GitOps repo** must work **without** GitHub Pages or a packaged `.tgz`. OpenShift GitOps / Argo CD points at this fork’s chart directory.

### Chart requirements for GitOps

- All resources use `{{ .Release.Namespace }}` (no `NAMESPACE_PLACEHOLDER`).
- ClusterRoleBinding subject namespace = release namespace.
- Nginx `/api/` upstream uses in-cluster DNS:  
  `http://{{ include "…backend" . }}.{{ .Release.Namespace }}.svc:8080`  
  (or short name if same namespace — prefer full for clarity).
- Image tags default to `v0.1.0` / Chart `appVersion`; overridable via values from the GitOps repo.
- Optional `imagePullSecrets` in values for private Quay (if repos are public, leave empty).
- `route.host` empty → OpenShift assigns host; GitOps can set it per cluster.
- No post-install Jobs that require interactive cluster login.

### Artifact for the other repo

Add `examples/gitops/application.yaml` (reference only; not applied by this chart) that the GitOps repo can copy/adapt:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: migration-toolkit-rhcl
  namespace: openshift-gitops
spec:
  project: default
  source:
    repoURL: https://github.com/maximilianoPizarro/migration-toolkit-rhcl
    targetRevision: main
    path: helm/migration-toolkit-rhcl
    helm:
      valueFiles:
        - values.yaml
      # Optional overlay from GitOps repo via multiple sources / valuesObject:
      valuesObject:
        route:
          host: ""   # set per cluster if needed
        backend:
          image:
            tag: latest   # provisional until first v* release
        frontend:
          image:
            tag: latest
  destination:
    server: https://kubernetes.default.svc
    namespace: migration-toolkit
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

### Provisional vs release path

| Path | When | How GitOps consumes |
|------|------|---------------------|
| **Provisional (git path)** | Before/without Pages | `source.path: helm/migration-toolkit-rhcl` + image tag `latest` or sha |
| **Release (Helm repo)** | After `v*` + Pages | `helm repo add` / Argo `chart` + `repoURL` Pages URL + semver |

### Acceptance for GitOps

1. From a clone of the GitOps repo (or `oc apply`), Application syncs chart from this fork’s `main`.
2. Pods pull Quay images (public **or** pull-secret wired in values).
3. Frontend Route works; UI can call backend `/api/*` via nginx proxy; backend Ready probe hits `/q/health/ready` on the backend Service.
4. No dependency on `docs/index.yaml` for this provisional path.

### Quay visibility

For provisional GitOps without cluster pull secrets, Quay repos should be **public**, or values must define `imagePullSecrets` and the GitOps repo must create that Secret in the target namespace.

## Replication note for mushino

When copying to the canonical repo, update at minimum:

- Quay namespace / image names
- `HELM_REPO_URL` and Chart `home` / `sources`
- Keep `maintainers: mushino` as the sole maintainer entry
- Point the GitOps `Application` `repoURL` / image repos at the canonical locations
