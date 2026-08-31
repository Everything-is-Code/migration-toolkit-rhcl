# Contributing

Thanks for contributing to **Migration Toolkit for Red Hat Connectivity Link**.

Code owners: see [`.github/CODEOWNERS`](.github/CODEOWNERS) (`@pcastelo`, `@fmenesesg`).

## Local setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java (OpenJDK) | 21+ | Backend |
| Apache Maven | 3.9.x+ | Backend build / tests |
| Node.js | **22** | Frontend (matches `frontend/Dockerfile.ci`) |
| npm | 9+ | Frontend deps |
| Docker / Podman | Latest | Optional local image builds |
| `oc` CLI | Matching OCP | Cluster deploy via `deploy/install.sh` |

Backend Quarkus platform version is **`3.27.5.1`** (`quarkus.platform.version` in `backend/pom.xml`).

### Backend

```bash
cd backend
mvn quarkus:dev
```

PostgreSQL must be reachable on `localhost:5432` (see `backend/src/main/resources/application.properties`).

### Frontend

```bash
cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

The SPA reads the API base URL from **Vite bake-time** `import.meta.env.VITE_API_URL` (`frontend/src/api/client.ts`). There is no runtime `REACT_APP_API_URL`. For OpenShift installs, `deploy/install.sh` sets `VITE_API_URL` during `vite build` and nginx may proxy `/api`.

CORS defaults to `http://localhost:5173`, `http://localhost:3000`, and `http://localhost:8080`. Override with `CORS_ORIGINS` / `QUARKUS_HTTP_CORS_ORIGINS` when needed.

## Tests

```bash
# Backend unit + integration (from backend/)
cd backend && mvn test
cd backend && mvn verify

# Static analysis / report (from backend/)
./generate-report.sh

# Frontend typecheck / unit (from frontend/)
npm run typecheck
npm test
```

## Pull requests

1. Branch from `main` (or the agreed feature/tracker branch).
2. Keep PRs focused; prefer review slices under ~400 authored lines when possible.
3. Request review from owners in [`.github/CODEOWNERS`](.github/CODEOWNERS) (primary reviewer for hygiene work: **@pcastelo**).
4. Link related issues (`Closes #…` / `Closes part of #…`).
5. Ensure CI is green before merge.

### CI notes (coverage)

PR checks upload **Codecov** coverage for backend (JaCoCo) and frontend (Vitest `npm run test:coverage`). Regression gates use `target: auto` / `threshold: 0%` in `codecov.yml` — coverage must not decrease vs the PR base. Local: `cd backend && mvn verify`; `cd frontend && npm run test:coverage`. After the first successful `frontend` upload on `main`, add `codecov/project/frontend` and `codecov/patch/frontend` to branch protection (same pattern as backend). Component coverage expansion: [#172](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/172).

## Conventional commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` new user-facing capability
- `fix:` bug fix
- `docs:` documentation only
- `chore:` tooling / hygiene
- `refactor:` no behavior change
- `test:` tests only

Examples:

```
docs: add CONTRIBUTING, AGENTS, SECURITY; align README versions
fix(deploy): namespace placeholder for images; drop REACT_APP_API_URL
```

## Coding standards

- **Backend**: Java 21, Quarkus patterns already in `backend/src`; prefer existing packages under `com.redhat.migrationtoolkit.rhcl`. Run Checkstyle/PMD via Maven when touching Java.
- **Frontend**: TypeScript strict, React 18, PatternFly 5, Vite. Prefer existing page/API patterns under `frontend/src`.
- **Deploy**: OpenShift manifests under `deploy/` use `NAMESPACE_PLACEHOLDER`; `install.sh` substitutes it with `sed` before `oc apply`. Do not hardcode a namespace in image registry paths.
- **Secrets**: never commit tokens, kubeconfigs, or `.env` files with credentials.
- **Scope**: avoid drive-by refactors unrelated to the PR.

## Container base digests

Dockerfile `FROM` lines in `backend/Dockerfile.jvm` and `frontend/Dockerfile.ci` are pinned as `image:tag@sha256:…` (tag kept for humans; digest is authoritative).

### Refresh procedure

1. Resolve current digests (requires network access to the registries):

```bash
skopeo inspect --format '{{.Digest}}' docker://registry.access.redhat.com/ubi9/openjdk-21:1.24
skopeo inspect --format '{{.Digest}}' docker://registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24
skopeo inspect --format '{{.Digest}}' docker://docker.io/library/node:22-alpine
skopeo inspect --format '{{.Digest}}' docker://registry.access.redhat.com/ubi9/nginx-124:1
```

(`crane digest <image>` is an equivalent alternative.)

2. Update the matching `FROM …@sha256:…` lines in `backend/Dockerfile.jvm` and `frontend/Dockerfile.ci`. Keep the human-readable tag in the reference and in the nearby comment.
3. Rebuild images and smoke-test HEALTHCHECK endpoints (`/q/health/ready` for backend, `/` for frontend nginx).

Do not drop digests back to floating tags without documenting why.
