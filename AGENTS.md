# Agent / AI contributor guide

Short map for automated agents and humans working in this repository.

**Primary reviewer**: [@pcastelo](https://github.com/pcastelo)  
**Code owners**: [`.github/CODEOWNERS`](.github/CODEOWNERS)

## Repository map

| Path | Role |
|------|------|
| `backend/` | Quarkus 3.27.x (Java 21) REST API, Flyway, tests |
| `frontend/` | React + PatternFly + Vite SPA |
| `deploy/` | OpenShift S2I manifests + `install.sh` |
| `helm/` | Helm chart packaging |
| `docs/` | Published Helm chart index (GitHub Pages) |
| `scripts/` | Seed / helper scripts |
| `testdata/` | Sample payloads for local demos |

## Spec-driven development (SDD)

This project uses **Engram-only** SDD artifacts (topic keys `sdd/{change-name}/…`). Do **not** create or rely on an `openspec/` tree unless maintainers explicitly switch modes.

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

## Do not

- Commit secrets, tokens, or private cluster credentials.
- Hardcode `migration-toolkit` (or any fixed ns) into deploy **image** paths — use `NAMESPACE_PLACEHOLDER`.
- Expand out-of-scope hygiene issues (e.g. PostgreSQL SUPERUSER, unrelated PF Table rewrites) into an active change without maintainer lock.
- Invent OpenSpec files when Engram is the active artifact store.
- Skip CODEOWNERS review for substantive PRs.
