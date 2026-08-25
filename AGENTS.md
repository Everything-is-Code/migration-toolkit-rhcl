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

This repo is the **consolidated** 3scale → Connectivity Link (Kuadrant) migration tool: one Quarkus backend + one React frontend doing export, compatibility check, and YAML conversion/apply in a single app. The sibling `rhcl-ai` program docs still describe an older split architecture (`3scaleextract` Go export tool + `apishift` Java/Angular migration tool) that predates this repo (first commit 2026-06-29) — treat `rhcl-ai/AGENTS.md`'s architecture/PO-priorities sections as stale until a maintainer reconciles them with this repo.

## Policy conversion architecture

`ConversionService` (~175 lines) orchestrates conversion: it builds `ConversionContext` and delegates to `ResourceGeneratorRegistry`. YAML generation lives in `service/generator/` (one `ResourceGenerator` per output file) and `service/generator/contributor/` (fragments for HTTPRoute, AuthPolicy, Secret).

- **Strategy + Registry** — `ResourceGenerator` per K8s output file (`gateway.yaml`, `httproute.yaml`, `policy.yaml`, `secret.yaml`, EnvoyFilters, optional TLS/DNS, etc.), looked up via CDI registry instead of hardcoded branches in `convert()`.
- **Collector/Contributor** — `httproute.yaml`, `policy.yaml`, and `secret.yaml` aggregate fragments from multiple 3scale policies via `*Contributor` beans against shared builders.

Full file list and conditions: [`docs/conversion-architecture.md`](https://github.com/Everything-is-Code/rhcl-sdd/blob/main/docs/conversion-architecture.md) in the SDD store (local: `../migration-toolkit-sdd/docs/conversion-architecture.md`).

Before adding a new policy conversion, check:
- **#149** — epic tracking recognized-but-unconverted 3scale policies.
- **#40** — add a new `*Contributor` (or generator only for a new output file); do **not** extend `ConversionService`.
- **#170** — extend `ReadmeSupport` / `ReadmeNotes`; do not add positional parameters to orchestrator APIs.

## Known performance hotspots

See **#169** for the full list (bulk convert is sequential across services, several N+1 3scale HTTP calls in `ThreeScaleExportService`, missing pagination on some 3scale client methods, `HistoryPage` not exposing the backend's existing pagination, cluster-wide Route listing without cache). Check that issue before adding new bulk-fetch code paths so fixes there and new features don't collide.

## Merge conventions learned from stacked PRs (Aug 2026)

- When merging a **chain** of dependent PRs (PR B based on PR A's branch), merge the root PR into `main` **without** deleting its branch, then `gh pr edit --base main` the dependent PR and expect a conflict — resolve by merging `origin/main` into the feature branch, not by rebasing. After #40, conflict hotspots in `ConversionService` are reduced; prefer editing generators/contributors for new policy work.
- **Windows/CRLF false failures**: local `mvn test` can show `ConversionServiceTest` failures on whitespace-sensitive YAML string assertions purely from `core.autocrlf=true` line endings, even when the change under review didn't touch `ConversionService.java` and CI (Linux) is green. Don't treat these as regressions without checking CI first.
- Every PR (and every dependent chain) gets a **Cursor AI Code Review** comment: standard disclaimer header, English, findings ranked Major/Moderate/Minor/Nit, no auto-approve unless explicitly asked. Non-blocking findings for a closing feature issue go into a consolidated follow-up tracking issue (pattern: #95, #98, #120, #123, #140, #141, #145, #147) instead of getting lost.

## Spec-driven development (SDD)

SDD artifacts live in the sibling OpenSpec **store** `../migration-toolkit-sdd/` (GitHub: `rhcl-sdd`, store id: `rhcl-sdd`).

- **Do not** create `openspec/` in this product repo — use the store.
- **Do not** use Engram for new work (legacy only; see gateforge `docs/archive/`).
- Workflow: `enrich-us` → `/opsx-propose` → `/opsx-apply` → tests → `/opsx-archive` → PR here.
- Backlog and standards: `migration-toolkit-sdd/docs/sdd-backlog.md`, `docs/base-standards.md`.

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
- Invent `openspec/` in this repo — use the `rhcl-sdd` store (`../migration-toolkit-sdd/`).
- Skip CODEOWNERS review for substantive PRs.
