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

Dockerfile base images may be pinned by digest (or documented for refresh) in a follow-up images hygiene change. When refreshing pins, resolve digests with `skopeo`/`crane`, update `backend/Dockerfile.jvm` / `frontend/Dockerfile.ci`, and note the human-readable tag in a comment next to the digest.
