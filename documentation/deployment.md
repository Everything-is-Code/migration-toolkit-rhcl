# Deployment

Install paths and maintainer operations. For local development, see [CONTRIBUTING.md](../CONTRIBUTING.md).

## Prerequisites (cluster)

| Component | Version | Notes |
|-----------|---------|-------|
| OpenShift | 4.14+ | Or compatible Kubernetes + Routes |
| CrunchyData PostgreSQL Operator | Latest | Install in `openshift-operators` **before** `install.sh` |
| Sail Operator (Istio) | Latest | Auto-installed by `install.sh` |
| RH Connectivity Link (`rhcl-operator`) | Latest | Productized operator — not community `kuadrant-operator` |

3scale: Admin Portal URL + Personal Access Token.

## Helm install (preferred)

Public images (defaults):

- `quay.io/everythingascode/migration-toolkit-rhcl-backend:latest`
- `quay.io/everythingascode/migration-toolkit-rhcl-frontend:latest`

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

Local chart:

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

Route: `oc -n migration-toolkit get route`

GitOps: [`examples/gitops/application.yaml`](../examples/gitops/application.yaml) with `path: helm/migration-toolkit-rhcl`.

Chart embeds PostgreSQL (`registry.redhat.io/rhel9/postgresql-15`) unless external DB is configured.

## OpenShift S2I (legacy)

```bash
NAMESPACE=migration-toolkit ./deploy/install.sh
# Partials: --backend-only, --frontend-only, --db-only
# Japanese installer strings: INSTALL_LANG=ja ./deploy/install.sh
```

`install.sh` creates namespace, installs Sail + RHCL operators, waits for Crunchy PostgreSQL, builds backend/frontend via S2I, prints URLs.

### Frontend S2I pitfall

`npm run build` empties `frontend/build/` and removes copied nginx config. Before manual `oc start-build ... --from-dir=frontend/build`:

```bash
mkdir -p frontend/build/nginx-default-cfg
cp frontend/nginx-default-cfg/api-proxy.conf frontend/build/nginx-default-cfg/api-proxy.conf
```

`install.sh` does this automatically.

## Maintainer notes

### GitHub Pages (Helm repo)

`/docs` on `main` serves the chart index. URL: `https://everything-is-code.github.io/migration-toolkit-rhcl/`

### CI secrets

| Secret | Purpose |
|--------|---------|
| `REDHAT_REGISTRY_USERNAME` / `REDHAT_REGISTRY_PASSWORD` | Pull UBI/RHEL bases in CI |
| `QUAY_USERNAME` / `QUAY_PASSWORD` | Push images (optional if using public `everythingascode` images) |

Workflows: `.github/workflows/pr-checks.yml`, `build-push-quay.yml`, `release.yml`.

### Releases

1. Bump `version` / `appVersion` in `helm/migration-toolkit-rhcl/Chart.yaml`.
2. Tag `vX.Y.Z` matching chart version.
3. Release workflow packages chart into `docs/` via PR — merge for Pages update.

### Post-deploy smoke test

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
oc -n migration-toolkit get pods,route
```

Confirm UI loads and `/api/*` reaches backend (nginx proxy or baked `VITE_API_URL`).
