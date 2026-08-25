# Agent / AI contributor guide

Short map for automated agents and humans working in this repository.

**Primary reviewer**: [@pcastelo](https://github.com/pcastelo)  
**Code owners**: [`.github/CODEOWNERS`](.github/CODEOWNERS)

## Repository map

| Path | Role |
|------|------|
| `backend/` | Quarkus 3.27.x (Java 21) REST API, Flyway, tests |
| `frontend/` | React + PatternFly + Vite SPA — `components/<domain>/` + thin `pages/` orchestrators; `AppStateContext` for workflow state |
| `deploy/` | OpenShift S2I manifests + `install.sh` |
| `helm/` | Helm chart packaging |
| `docs/` | Published Helm chart index (GitHub Pages) |
| `scripts/` | Seed / helper scripts |
| `testdata/` | Sample payloads for local demos |

## Git / branches

- Feature work: `feature/<issue>-short-description` from `main` — **do not commit implementation on `main`**
- OpenSpec `/opsx-apply` edits this repo; create the feature branch before the first implementation commit (see sibling `migration-toolkit-sdd/docs/base-standards.md`)

## Conversion architecture

Full file list and conditions: [`docs/conversion-architecture.md`](https://github.com/Everything-is-Code/rhcl-sdd/blob/main/docs/conversion-architecture.md) in the SDD store (local: `../migration-toolkit-sdd/docs/conversion-architecture.md`).

## Spec-driven development (SDD)

OpenSpec store: sibling `migration-toolkit-sdd/` (GitHub: [`rhcl-sdd`](https://github.com/Everything-is-Code/rhcl-sdd)). This repo holds product code only.

## Frontend API URL (important)

- Client base URL: `import.meta.env.VITE_API_URL` in `frontend/src/api/client.ts`.
- Local: `VITE_API_URL=http://localhost:8080 npm run dev`.
- Cluster: `deploy/install.sh` bakes `VITE_API_URL` at Vite build time; nginx may proxy `/api`.
- **Do not** reintroduce runtime `REACT_APP_*` env vars for the SPA — they are not read by the Vite client.

## Deploy namespace placeholder

Manifests use `NAMESPACE_PLACEHOLDER` in metadata **and** image refs, e.g.:

`image-registry.openshift-image-registry.svc:5000/NAMESPACE_PLACEHOLDER/migration-tool-backend:latest`

`deploy/install.sh` already runs `sed` replacing `NAMESPACE_PLACEHOLDER` with the target namespace. Prefer reusing that token; do not invent a second substitution pattern without need.

## Test commands

```bash
cd backend && mvn test
cd backend && mvn verify
cd backend && ./generate-report.sh

cd frontend && npm run typecheck
cd frontend && npm test
```

## Container base digests

`backend/Dockerfile.jvm` and `frontend/Dockerfile.ci` pin bases as `image:tag@sha256:…`. When Dependabot or security advisories require a bump:

1. `skopeo inspect --format '{{.Digest}}' docker://<image:tag>` (or `crane digest`)
2. Update the Dockerfile `FROM` digest; keep the tag comment for humans
3. Rebuild and confirm HEALTHCHECK still passes (`/q/health/ready`, frontend `/`)

Full command list: see **Container base digests** in `CONTRIBUTING.md`.

## Do not

- Commit secrets, tokens, or private cluster credentials.
- Hardcode `migration-toolkit` (or any fixed ns) into deploy **image** paths — use `NAMESPACE_PLACEHOLDER`.
- Expand out-of-scope hygiene issues (e.g. PostgreSQL SUPERUSER, unrelated PF Table rewrites) into an active change without maintainer lock.
- Invent OpenSpec change folders in this repo — planning lives in `migration-toolkit-sdd/openspec/`.
- Skip CODEOWNERS review for substantive PRs.
