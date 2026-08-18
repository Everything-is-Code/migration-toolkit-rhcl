# migration-toolkit-rhcl Helm chart

Deploys the Migration Toolkit (Quarkus backend, nginx frontend, optional PostgreSQL) on OpenShift.

Chart version **1.0.0** hardens secrets, persistence, pod security, optional NetworkPolicy, and RBAC notes.

## Maintainer

- mushino

## Install (cluster)

Password is **required** (or supply an existing Secret):

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace \
  --set postgresql.password='CHANGE_ME'
```

### existingSecret

Create a Secret with keys `DB_USER`, `DB_PASSWORD`, and `DB_NAME`, then:

```bash
helm upgrade --install migration-toolkit-rhcl ./helm/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace \
  --set postgresql.existingSecret=my-db-secret
```

When `existingSecret` is set, the chart skips emitting DB keys into `*-config` (3scale token keys may still use that Secret).

## Persistence

Embedded Postgres uses a PVC (`postgresql.persistence`, default `8Gi`, cluster default StorageClass).

**Upgrade warning:** chart 0.1.0 used `emptyDir`. Upgrading to 1.0.0 does **not** migrate that data — export/backup first if needed.

## NetworkPolicy

`networkPolicy.enabled` defaults to `false`. When enabled, stock allows only:

| From | To | Port |
|------|----|------|
| OpenShift router (`network.openshift.io/policy-group: ingress`) | frontend | 8080 |
| frontend | backend | 8080 |
| backend | postgresql | 5432 |

```bash
--set networkPolicy.enabled=true
```

## RBAC

| Mode | Behavior |
|------|----------|
| `rbac.clusterAdmin: false` (default) | ClusterRole mirrors `deploy/backend/06-rbac.yaml` (apply `resources: ["*"]` on listed apiGroups + detection get/list rules) |
| `rbac.clusterAdmin: true` | Binds backend SA to `cluster-admin` (not recommended) |

## Pod security

Backend, frontend, and Postgres use OpenShift-safe defaults: `runAsNonRoot`, `allowPrivilegeEscalation: false`, drop `ALL` capabilities. **`readOnlyRootFilesystem` is not set by default** (unsafe for this Postgres image). Override via `*.podSecurityContext` / `*.securityContext`.

## PostgreSQL image

Pinned digest (RHBA-2026:9103 amd64), not `latest`:

`registry.redhat.io/rhel9/postgresql-15@sha256:df15212ae9cc010ebe9dd61b19332a845c8b7df3dd00fb70e47d6e925f8b75e7`

Re-verify when bumping: `skopeo inspect docker://registry.redhat.io/rhel9/postgresql-15:latest`

## GitOps (provisional)

Point OpenShift GitOps / Argo CD at this repository path `helm/migration-toolkit-rhcl` (see `examples/gitops/application.yaml`). No GitHub Pages Helm repo required for day-1 sync.

## Values

| Key | Default | Description |
|-----|---------|-------------|
| `backend.image.repository` | `quay.io/everythingascode/migration-toolkit-rhcl-backend` | Backend image |
| `frontend.image.repository` | `quay.io/everythingascode/migration-toolkit-rhcl-frontend` | Frontend image |
| `postgresql.enabled` | `true` | Deploy embedded PostgreSQL |
| `postgresql.password` | `""` | **Required** unless `existingSecret` is set |
| `postgresql.existingSecret` | `""` | Optional Secret with `DB_*` keys |
| `postgresql.persistence.enabled` | `true` | PVC instead of emptyDir |
| `postgresql.persistence.size` | `8Gi` | PVC size |
| `networkPolicy.enabled` | `false` | Optional NetworkPolicies |
| `route.enabled` | `true` | Create OpenShift Route to frontend |
| `rbac.clusterAdmin` | `false` | Opt-in cluster-admin binding |
| `imagePullSecrets` | `[]` | Pull secrets for private Quay |
