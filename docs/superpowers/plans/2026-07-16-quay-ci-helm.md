# Quay CI + Helm Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Dockerfiles, Quay build/release GitHub Actions, version scripts, and a GitOps-ready Helm chart for migration-toolkit-rhcl (apishift-slim).

**Architecture:** Multi-stage UBI images push to `quay.io/maximilianopizarro/migration-toolkit-rhcl-{backend,frontend}` on main; tag `v*` packages Helm into `docs/` and opens a PR. Chart deploys postgres + backend + frontend + Route + RBAC; Argo CD consumes via git path.

**Tech Stack:** Quarkus 3.8 / Java 21, Vite React, UBI9 OpenJDK + nginx-124, PostgreSQL 15 UBI, Helm 3, GitHub Actions, OpenShift Routes.

## Global Constraints

- Quay backend: `quay.io/maximilianopizarro/migration-toolkit-rhcl-backend`
- Quay frontend: `quay.io/maximilianopizarro/migration-toolkit-rhcl-frontend`
- Chart path: `helm/migration-toolkit-rhcl`; version/appVersion `0.1.0`
- Chart maintainer: **mushino only**
- Backend DB env names: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- Frontend Vite `outDir` is `build` (not `dist`)
- Do not modify `deploy/` S2I manifests
- Secrets already in GitHub: `QUAY_*`, `REDHAT_REGISTRY_*`
- Spec: `docs/superpowers/specs/2026-07-16-quay-ci-helm-design.md`

## File map

| Path | Role |
|------|------|
| `backend/Dockerfile.jvm` | Backend image |
| `backend/.dockerignore` | Slim build context |
| `frontend/Dockerfile.ci` | Frontend image |
| `frontend/.dockerignore` | Slim build context |
| `scripts/lib/{common,version,helm-repo-url}.sh` | CI versioning |
| `.github/workflows/build-push-quay.yml` | Push images on main |
| `.github/workflows/release.yml` | Tag release + Helm docs PR |
| `helm/migration-toolkit-rhcl/**` | Chart |
| `docs/index.yaml` | Empty Helm repo index seed |
| `examples/gitops/application.yaml` | Argo CD sample |

---

### Task 1: Dockerfiles

**Files:**
- Create: `backend/Dockerfile.jvm`, `backend/.dockerignore`
- Create: `frontend/Dockerfile.ci`, `frontend/.dockerignore`

- [x] **Step 1: Backend Dockerfile** — multi-stage UBI openjdk-21 builder + runtime; `mvn package -DskipTests`; copy quarkus-app layers; EXPOSE 8080; USER 185.
- [x] **Step 2: Frontend Dockerfile** — Node 22 builder `npm ci` + `npm run build`; copy `build/` into UBI nginx-124; COPY `nginx/nginx.conf` to `${NGINX_CONF_PATH}` (default upstream can be localhost; Helm overrides via ConfigMap).
- [x] **Step 3: .dockerignore** for both (skip `target/`, `node_modules/`, tests noise).
- [x] **Step 4: Commit** `feat: add backend and frontend Dockerfiles for Quay builds`

### Task 2: Version scripts

**Files:**
- Create: `scripts/lib/common.sh`, `scripts/lib/version.sh`, `scripts/lib/helm-repo-url.sh`

- [x] **Step 1: Implement scripts** reading `helm/migration-toolkit-rhcl/Chart.yaml` and exporting `HELM_REPO_URL=https://maximilianopizarro.github.io/migration-toolkit-rhcl/`
- [x] **Step 2: Commit** `chore: add version and helm-repo helper scripts`

### Task 3: GitHub Actions workflows

**Files:**
- Create: `.github/workflows/build-push-quay.yml`
- Create: `.github/workflows/release.yml`

- [x] **Step 1: build-push-quay.yml** — matrix for both images; Quay + RH registry logins; tags latest / vVERSION / sha.
- [x] **Step 2: release.yml** — tag validation, image push, helm package to docs/, index merge, PR, GitHub Release.
- [x] **Step 3: Commit** `ci: add Quay build-push and release workflows`

### Task 4: Helm chart + GitOps example

**Files:**
- Create: `helm/migration-toolkit-rhcl/Chart.yaml`, `values.yaml`, `values.schema.json`, `README.md`
- Create: `helm/migration-toolkit-rhcl/templates/*` (helpers, backend, frontend, nginx cm, postgres, secret, sa, clusterrole, route)
- Create: `docs/index.yaml` (empty apiVersion/entries seed)
- Create: `examples/gitops/application.yaml`

- [x] **Step 1: Chart metadata + values** — maintainer mushino; Quay images; postgres migrationtool; route/rbac/imagePullSecrets.
- [x] **Step 2: Templates** — GitOps-safe namespaces; DB_* env; nginx ConfigMap with release DNS; RBAC from deploy/06; Route edge.
- [x] **Step 3: examples/gitops + docs/index.yaml seed**
- [x] **Step 4: Run** `helm lint helm/migration-toolkit-rhcl` and `helm template test helm/migration-toolkit-rhcl` — expect success.
- [x] **Step 5: Commit** `feat: add Helm chart and GitOps Application example`

### Task 5: Verify and handoff

- [x] **Step 1:** Confirm `source scripts/lib/version.sh` prints `0.1.0` / `v0.1.0`
- [x] **Step 2:** Summarize for user: push main to trigger Quay build; copy `examples/gitops/application.yaml` into GitOps repo; ensure Quay public or pull secrets.

---

## Spec coverage checklist

- [x] Dockerfiles backend/frontend
- [x] build-push-quay + release workflows
- [x] version / helm-repo scripts
- [x] Helm chart templates + mushino maintainer
- [x] docs Helm index seed + GitOps example
- [x] deploy/ untouched
- [x] DB_* env mapping
