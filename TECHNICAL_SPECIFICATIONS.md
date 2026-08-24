# Technical Specifications — Migration Toolkit for Red Hat Connectivity Link

Reference document for engineers and coding agents working on this repository. It describes the stack, environment setup, architecture, data model, and conventions **as they actually exist in the code today**, plus a dedicated section of recommended improvements where current practice has gaps.

Companion Cursor rules (loaded automatically when editing matching files):

| Rule | Applies to |
|---|---|
| [`.cursor/rules/data-model.mdc`](.cursor/rules/data-model.mdc) | `backend/.../entity/**`, `backend/.../dto/**`, Flyway migrations |
| [`.cursor/rules/testing-standards.mdc`](.cursor/rules/testing-standards.mdc) | `backend/src/test/**`, `frontend/src/**/*.test.ts(x)` |
| [`.cursor/rules/frontend-standards.mdc`](.cursor/rules/frontend-standards.mdc) | `frontend/src/**/*.ts(x)` |

See also: [`README.md`](README.md) (features/workflow/API reference), [`CONTRIBUTING.md`](CONTRIBUTING.md) (PR process), [`AGENTS.md`](AGENTS.md) (agent-specific context), [`SECURITY.md`](SECURITY.md).

---

## 1. Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Quarkus **3.27.5.1**, RESTEasy Reactive (`quarkus-rest-jackson`), Hibernate ORM **Panache**, `quarkus-hibernate-validator`, `quarkus-rest-client-jackson`, SnakeYAML 2.6 |
| Database | PostgreSQL (CrunchyData Operator in-cluster), Flyway migrations (`V1`–`V9`) |
| Kubernetes client | Fabric8 Kubernetes Client 6.7.x (`quarkus-kubernetes-client`) |
| API docs | SmallRye OpenAPI + Swagger UI (`/q/swagger-ui`) |
| Frontend | React 18, PatternFly 5 (`@patternfly/react-core/table/icons`), TypeScript 5 (`strict: true`), Vite 4, `react-router-dom` 6, Axios, `i18next` / `react-i18next` |
| Backend testing | JUnit 5 (`quarkus-junit5`), REST-assured, Mockito (`quarkus-junit5-mockito`), `quarkus-panache-mock`, H2 (test profile), **ArchUnit** (layering rules), Playwright (E2E), Awaitility |
| Backend static analysis | Checkstyle, PMD, JaCoCo (merged unit+Quarkus coverage) |
| Frontend testing/lint | Vitest, ESLint 9 (flat config, `typescript-eslint`), `tsc --noEmit` |
| Deployment | OpenShift S2I *or* Helm chart, nginx (frontend static + `/api` reverse proxy), Docker/Podman, GitHub Actions CI/CD, Quay.io image registry |

Full dependency lists: [`backend/pom.xml`](backend/pom.xml), [`frontend/package.json`](frontend/package.json).

## 2. Development environment setup

| Tool | Version | Purpose |
|---|---|---|
| Java (OpenJDK) | 21+ | Backend build |
| Apache Maven | 3.9.x+ | Backend build/tests |
| Node.js | **22** (matches `frontend/Dockerfile.ci`) | Frontend build |
| npm | 9+ | Frontend deps |
| Docker/Podman | latest | Local image builds (optional) |
| `oc` CLI | matching OCP | `deploy/install.sh` |

```bash
# Backend (PostgreSQL must be reachable on localhost:5432)
cd backend && mvn quarkus:dev

# Frontend (separate terminal)
cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

- Frontend API base URL is **Vite bake-time** `import.meta.env.VITE_API_URL` (`frontend/src/api/client.ts`) — never a runtime `REACT_APP_*` var.
- CORS default allowlist: `http://localhost:5173`, `:3000`, `:8080` — override with `CORS_ORIGINS` / `QUARKUS_HTTP_CORS_ORIGINS`.
- Export/API tokens must be sent as `Authorization: Bearer <token>`, never as a query parameter.

Test commands: see [`.cursor/rules/testing-standards.mdc`](.cursor/rules/testing-standards.mdc) or the **Testing structure** section below.

## 3. Architecture & file structure

```
                +----------------------+
                |      Web UI          |
                |  (React/PatternFly)  |
                +----------+-----------+
                           |  REST API (JSON)
        +------------------+-------------------+
        |        Quarkus Backend (Java 21)     |
        | Export -> Compatibility -> Convert -> |
        | Validate -> Package -> Apply/Import   |
        | -> History -> Gateway Info -> Setup   |
        +------------------+-------------------+
                    |                  |
              3scale Admin API    PostgreSQL (Flyway)
                    |
        Kuadrant / Gateway API / Istio YAML
```

**Backend package layout** (`backend/src/main/java/com/redhat/migrationtoolkit/rhcl/`, 44 files):

| Package | Files | Role |
|---|---|---|
| `controller/` | 14 | JAX-RS endpoints (HTTP entry point) |
| `service/` | 6 | Business logic (`@ApplicationScoped`) |
| `entity/` | 3 | Panache/JPA entities — see [data model](#4-data-model) |
| `dto/` | 7 | Request/response payload shapes |
| `model/` | 11 | Internal domain model (3scale resource graph) |
| `client/` | 1 | REST-client interface to the 3scale Admin API |
| `util/` | 2 | Constants + i18n `ResourceBundle` wrapper |

Layering is enforced by ArchUnit (`backend/src/test/architect/.../ArchitectureTest.java`): `controller → service → client`; controllers must not depend on `client` directly; only `service`/`model` may be `*Service`; entities must not depend on `controller`. See [`testing-standards.mdc`](.cursor/rules/testing-standards.mdc) for the full rule list.

**Frontend layout** (`frontend/src/`, 39 files incl. tests):

| Folder | Role |
|---|---|
| `pages/` | One PatternFly page component per route (10 routes) + co-located non-JSX business logic (`compatibilityChecks.ts`, `clusterCapabilityUi.ts`) |
| `api/` | `client.ts` (Axios instance + per-domain functions), `types.ts` (all request/response interfaces) |
| `locales/` | `en.json` / `ja.json` (react-i18next resources) |
| `styles/` | `pfTokens.ts` (PatternFly CSS-var wrappers), `shared.module.css` |
| `utils/` | Pure helpers (`appStateStorage.ts`, `fixHttpRoutePort.ts`, `timezone.ts`) |

No `components/` or `hooks/` folder exists — small subcomponents are co-located inside their page file. Full conventions: [`frontend-standards.mdc`](.cursor/rules/frontend-standards.mdc).

**API surface**: see [`README.md#api-reference`](README.md#api-reference) for the full endpoint table; Swagger UI at `/q/swagger-ui`.

## 4. Data model

Three Panache entities (`ProjectEntity`, `ConversionHistoryEntity`, `AppSettingsEntity`), Flyway-migrated `V1`–`V9`. Full field-by-field spec, JSON-column conventions, and suggested improvements (enum columns, `jsonb`): [`.cursor/rules/data-model.mdc`](.cursor/rules/data-model.mdc).

Condensed view:

```
Project 1---* ConversionHistory
Project:            id, name, threescaleUrl, tenant, createdAt, updatedAt
ConversionHistory:  id, project_id, source(CONVERT|IMPORT), serviceId, serviceName,
                     namespace, status(COMPLETED|PARTIAL|FAILED|IN_PROGRESS),
                     compatibilityScore, totalCount, successCount, failureCount,
                     failureDetails(JSON text), exportedYaml(JSON text), yamlContent,
                     packageName, createdAt
AppSettings:         settings_key (PK), value (TEXT), updatedAt   -- generic k/v store
```

## 5. Design patterns and conventions

### 5.1 API design patterns (backend)

- REST/JAX-RS via RESTEasy Reactive: `@Path("/api/...")`, `@Produces/@Consumes(APPLICATION_JSON)`, MicroProfile OpenAPI annotations (`@Tag`, `@Operation`) — **no `@APIResponse`** used anywhere yet.
- Dependency injection: **field injection** with `@Inject` everywhere (no constructor injection observed) — follow the existing pattern for consistency rather than introducing constructor injection ad hoc.
- Request validation: `@Valid` on the DTO parameter (`ConversionRequest`, `ConnectionRequest`) triggers automatic 400s from Bean Validation; additional business-rule checks are inline `if` statements returning `Response.status(400).entity(Map.of("error", ...))`.
- Response shapes: mostly typed DTOs (`ClusterVersionsResponse`, `ValidationResult`, `ServiceListPage`) or **records nested inside the controller itself** for endpoint-specific shapes (`ApplyController.ApplyResult`, `SetupController.SetupResponse`) — no shared response envelope.
- `@Transactional` is applied at the **controller** method level (not inside services) wherever Panache entities are persisted.
- File upload uses RESTEasy Reactive `@RestForm("file") FileUpload` (multipart) — see `ImportController`.

### 5.2 Testing structure

Full spec (ArchUnit rules, naming, frameworks, Vitest conventions): [`.cursor/rules/testing-standards.mdc`](.cursor/rules/testing-standards.mdc). Summary: JUnit5 + REST-assured + Mockito + H2 for backend, ArchUnit for layering, Playwright for E2E; Vitest for frontend logic/util tests only (**no React component tests exist today**).

### 5.3 Naming conventions

| Scope | Convention |
|---|---|
| Backend packages | `com.redhat.migrationtoolkit.rhcl.{controller,service,entity,dto,model,client,util}`, singular, lowercase |
| Backend classes | Suffix by role: `*Controller`, `*Service`, `*Entity`, `*Client`; DTOs have **no** `Dto` suffix (`ConversionRequest`, not `ConversionRequestDto`) |
| Backend constants | `UPPER_SNAKE_CASE`, grouped in a `*Constants` class with a private constructor |
| Backend logger | Always `private static final Logger LOG = Logger.getLogger(<Class>.class);` |
| Backend test methods | `methodUnderTest_condition_expectedResult` |
| Frontend files | PascalCase for page/component files, camelCase for non-component utils |
| Frontend types | `interface` for object shapes, `type` for string-literal unions; `Request`/`Response`/`Result`/`Page` suffixes for API payloads |
| Frontend handlers | `handleX` inside a component, `onX` for a prop passed to a child |
| Frontend booleans | `isX` or a participle (`loading`, `applying`) |
| i18n keys | `{page}.{camelCaseKey}`, prefixes `btnX`/`labelX`/`colX`/`errorX`/`ariaX` |

### 5.4 Git workflow

- Default branch: `main`. Branch from `main` per issue/feature.
- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:` — see [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Keep PRs focused; prefer review slices under ~400 authored lines. Link issues (`Closes #…`).
- Review required from [`.github/CODEOWNERS`](.github/CODEOWNERS) (`@pcastelo`, `@fmenesesg`). CI must be green before merge.
- **Stacked/dependent PRs**: merge the root PR into `main` first (keep its branch), retarget the dependent PR's base to `main`, then resolve the resulting conflict by merging `main` in — don't rebase a PR that's already open for review. Conflicts concentrate in `ConversionService.java` and `generateReadme(...)`'s argument list (tracked in [#170](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/170)).
- Every PR gets an AI code-review comment (standard disclaimer, English, findings ranked Major/Moderate/Minor/Nit, never auto-approve unless asked).

### 5.5 Clean Code / SOLID

- Controllers/services are generally single-purpose and thin, **except `ConversionService`** (~1300 lines), which currently violates SRP: it inlines YAML generation for every 3scale policy across every output file. This is a known, tracked gap — see [#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40).
- The rest of the backend follows the layering ArchUnit enforces (dependency inversion between layers is structurally guaranteed, not just convention).
- Frontend pages are not further decomposed (no extraction of subcomponents into their own files) — acceptable at current size, but watch pages growing past ~500 lines (`ImportPage.tsx`, `ConnectionPage.tsx` are already large).

### 5.6 Design patterns — current and planned

| Pattern | Where |
|---|---|
| Repository (implicit) | Panache active-record entities (`ProjectEntity.findLatestByServiceId`, etc.) |
| Cache-aside (manual) | `ThreeScaleExportService`'s `ConcurrentHashMap` + TTL caches (`exportCache`, `backendCatalogCache`, `applicationsCache`); keys include a SHA-256 token fingerprint to prevent cross-tenant cache leaks |
| Request coalescing | `ClusterVersionService`'s `ConcurrentHashMap<String, CompletableFuture<...>>` collapses concurrent detects into one in-flight probe |
| Error boundary | `App.tsx`'s `RouteErrorBoundary` (React class component) |
| **Planned**: Strategy + Registry | One `ResourceGenerator` per output K8s file, looked up via a registry instead of hardcoded in `ConversionService.convert()` — see [#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40) |
| **Planned**: Collector/Contributor | Each policy contributes a fragment to a shared builder for multi-policy files (`httproute.yaml`, `policy.yaml`, `secret.yaml`) instead of inline `if`s — see [#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40) and the README-notes variant in [#170](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/170) |

### 5.7 Form validation patterns

Backend: Bean Validation (`@NotBlank`/`@NotEmpty`) on request DTOs via `@Valid`, plus inline manual `if` checks for cross-field business rules (e.g. `dnsHostname` required when `includeDnsPolicy=true`).

Frontend: no schema library. Manual `if` + boolean state, surfaced via PatternFly `FormGroup`/`FormHelperText` (preferred, e.g. `ConnectionPage`) or a plain `<span>` with a CSS-module error class (e.g. `ImportPage`). Submit buttons are disabled rather than showing a submit-time error when a required field is missing. Full detail: [`frontend-standards.mdc`](.cursor/rules/frontend-standards.mdc).

### 5.8 Error handling

- **Backend**: no custom exception classes, no `ExceptionMapper`/`@ServerExceptionMapper` anywhere in the codebase. The pattern is `try { ... } catch (Exception e) { LOG.warnf(e, ...); return Response.status(...).entity(Map.of("error", ...)).build(); }` repeated per endpoint, with an **inconsistent error body shape** across controllers (`{"error": "..."}` vs `{"success": false, "message": "..."}` vs typed result records).
- **Frontend**: each page extracts `e.response?.data?.error || e.response?.data?.message || e.message || fallback` and renders it in an `Alert variant="danger"` — this extraction logic is duplicated per page rather than shared.

### 5.9 Logging

- Backend: exclusively `org.jboss.logging.Logger`, always named `LOG`, always with placeholder methods (`LOG.infof(...)`, `LOG.warnf(...)`, `LOG.debugf(...)`) — never string concatenation, never `System.out`. Enforced by ArchUnit. Levels: `INFO` for milestones, `WARN` for recoverable/soft-fail paths, `DEBUG` for low-level probe detail.
- Secrets are redacted before logging: `ClusterVersionService.sanitize()` strips tokens/`bearer`/`sha256~`/kubeconfig paths from any message that might reach a log or an error response.
- Frontend: `console.error` is used **only** inside the two error boundaries (`App.tsx`, `ImportPage.tsx`) — no other console logging, no client-side log shipping.

### 5.10 Cybersecurity practices

- Tokens travel as `Authorization: Bearer <token>` only — query-string tokens intentionally unsupported.
- Cache keys include a SHA-256 fingerprint of the access token (`ThreeScaleExportService.tokenFingerprint()`) so two tenants/users can never share cached data.
- `ConversionConstants.CREDENTIAL_PLACEHOLDER = "REPLACE_ME"` is written into generated `secret.yaml` instead of real credentials; `ValidationService` flags this placeholder if left unreplaced before apply.
- RBAC least-privilege: `ApplyController` provisions a `Role`/`RoleBinding` (or `ClusterRole` when needed) scoped to the exact `apiGroup`s/verbs required (Istio, Gateway API, Kuadrant, core), rather than a broad admin binding.
- Frontend never persists the access token: `sessionStorage` persistence (`appStateStorage.ts`) explicitly strips `accessToken`/`connected` before saving.
- CORS is allowlist-based, overridable via env var, never `*`.
- **Gap**: the backend itself has no authentication/authorization layer of its own — it trusts whatever 3scale/cluster credentials the caller supplies per request. Acceptable for a local/admin tool, but relevant if this is ever exposed beyond a trusted network (see suggestions below).

### 5.11 Code documentation & comments

- Javadoc/JSDoc is sparse and used only where a field's meaning is non-obvious (e.g. `ServiceListPage.total` nullability), not on every method.
- Prefer comments that explain **why** (invariants, security constraints — e.g. the `I-2`/`I-3` markers in `appStateStorage.ts` referencing the "never persist the token" invariant) over comments that restate the code.
- i18n message keys with inline default text (`t('key', 'Default text')`) double as lightweight documentation of a string's purpose when the translation doesn't exist yet.
- No repo-wide Javadoc/TSDoc generation is configured — don't add one without a maintainer decision, since none of the current docs pipeline depends on it.

## 6. Applicability across backend / frontend / mobile

These conventions are written to apply uniformly across every client surface of this program:

- **Backend**: sections 5.1–5.11 above apply directly; entity/testing detail lives in [`data-model.mdc`](.cursor/rules/data-model.mdc) and [`testing-standards.mdc`](.cursor/rules/testing-standards.mdc).
- **Frontend (current, only client today)**: full detail in [`frontend-standards.mdc`](.cursor/rules/frontend-standards.mdc) — component structure, API client, form validation, error handling, state, i18n, styling, naming.
- **Mobile**: **not implemented today** — there is no native or React Native client. If one is built, it should reuse `frontend/src/api/types.ts` contracts, the same error-message-extraction convention, and the same i18n key structure/locale files documented in `frontend-standards.mdc`, rather than duplicating them.

## 7. Suggestions / recommended improvements

Gaps identified while writing this document, prioritized. Items already tracked as GitHub issues are linked; the rest are new observations from this review.

| # | Area | Suggestion | Tracking |
|---|---|---|---|
| 1 | Backend architecture | Split `ConversionService` with Strategy+Registry (per-file generators) and Collector/Contributor (per-policy fragments) | [#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40) |
| 2 | Backend error handling | Introduce a `@ServerExceptionMapper`/custom exception hierarchy and a **single** error-response envelope (`{error, code, details}`) instead of 3 inconsistent ad-hoc shapes across controllers | New — not yet tracked |
| 3 | Backend performance | Parallelize bulk convert, fix N+1 3scale calls, expose backend pagination in `HistoryPage` | [#169](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/169) |
| 4 | Policy coverage | Implement the 19 recognized-but-unconverted 3scale policies | [#149](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/149) (+ 19 child issues) |
| 5 | `generateReadme` signature | Replace the growing positional-argument list with a `ReadmeNotes` collector | [#170](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/170) |
| 6 | Frontend testing | Add `@testing-library/react` + `jsdom`; currently 0% of React components are tested (only pure-logic/util tests exist) | New — not yet tracked |
| 7 | Frontend error handling | Extract the duplicated `apiErrorMessage(e, fallback)` helper into `frontend/src/utils/apiError.ts` | New — not yet tracked |
| 8 | Data model | Use `@Enumerated(EnumType.STRING)` for `status`/`source` instead of free-form `String`; consider native `jsonb` for `failureDetails`/`exportedYaml` | New — not yet tracked |
| 9 | Backend security | Consider whether the tool ever runs outside a trusted network; if so, add an auth layer in front of the backend's own API (today it has none — it only proxies caller-supplied 3scale/cluster credentials) | New — not yet tracked |
| 10 | Form validation | Once a 3rd/4th frontend form repeats the same manual-`if` pattern, extract a small shared validation helper rather than duplicating it again | New — not yet tracked |
| 11 | Documentation | This document and its 3 companion `.cursor/rules/*.mdc` files should be revisited whenever `ConversionService` is refactored (#40) or the error-handling envelope changes (#2 above), since several sections describe the current (pre-refactor) shape on purpose | New — not yet tracked |

---

*Generated from a direct audit of `backend/src` and `frontend/src` on 2026-08-20. Re-run the audit after major refactors (#40, #169) to keep this document accurate.*
