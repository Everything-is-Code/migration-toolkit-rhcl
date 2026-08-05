# migration-toolkit-rhcl Helm chart

Deploys the Migration Toolkit (Quarkus backend, nginx frontend, optional PostgreSQL) on OpenShift.

## Maintainer

- mushino

## Install (cluster)

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

## GitOps (provisional)

Point OpenShift GitOps / Argo CD at this repository path `helm/migration-toolkit-rhcl` (see `examples/gitops/application.yaml`). No GitHub Pages Helm repo required for day-1 sync.

## Values

| Key | Default | Description |
|-----|---------|-------------|
| `backend.image.repository` | `quay.io/maximilianopizarro/migration-toolkit-rhcl-backend` | Backend image |
| `frontend.image.repository` | `quay.io/maximilianopizarro/migration-toolkit-rhcl-frontend` | Frontend image |
| `postgresql.enabled` | `true` | Deploy embedded PostgreSQL |
| `route.enabled` | `true` | Create OpenShift Route to frontend |
| `imagePullSecrets` | `[]` | Pull secrets for private Quay |
