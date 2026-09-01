# STATUS_CHECK_MATRIX

Canonical map of GitHub Actions / Codecov checks for **Migration Toolkit RHCL**.
Keep this file aligned with `.github/workflows/*` and branch protection on `main`.

Related: umbrella CI hygiene (`ci-hygiene-gates`), [#199](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/199), [#204](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/204), [#209](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/209).

## Required on `main` (branch protection)

These contexts must be green before merging to `main` (exact Actions / Codecov display names):

| Check (display name) | Workflow / source | Trigger | Notes |
|----------------------|-------------------|---------|-------|
| Backend style (Checkstyle) | [`.github/workflows/pr-checks.yml`](../.github/workflows/pr-checks.yml) (`backend-style`) | `pull_request` + `push` to `main` | Always |
| Backend static (PMD) | `pr-checks.yml` (`backend-static`) | PR + push `main` | Always |
| Backend tests & coverage | `pr-checks.yml` (`backend-tests-cov`) | PR + push `main` | `mvn verify` (Playwright E2E excluded from PR gate intent); uploads JaCoCo to Codecov |
| Frontend quality | `pr-checks.yml` (`frontend-quality`) | PR + push `main` | typecheck, lint, test, build |
| PR quality summary | `pr-checks.yml` (`quality-summary`) | PR + push `main` | Aggregate gate over the four jobs above |
| Gitleaks secret scan | [`.github/workflows/pr-gitleaks.yml`](../.github/workflows/pr-gitleaks.yml) | `pull_request` | Secret scan (#209) |
| PR ↔ issue traceability | [`.github/workflows/pr-traceability-check.yml`](../.github/workflows/pr-traceability-check.yml) | PR (non-draft) | Requires `Closes` / `Fixes` / `Resolves` #N in PR body (#209) |
| codecov/patch/backend | Codecov (from `backend-tests-cov` upload) | After successful coverage upload | Patch coverage must not decrease (`codecov.yml`, #199) |

## Advisory / conditional (not required contexts today)

| Check (display name) | Workflow / source | When | Notes |
|----------------------|-------------------|------|-------|
| Validate en/ja locale parity | [`.github/workflows/pr-i18n-validation.yml`](../.github/workflows/pr-i18n-validation.yml) | PR paths: `frontend/src/locales/**`, `frontend/src/utils/apiError.ts`, or the workflow file | **Blocking when the workflow runs**; not listed in branch protection |
| codecov/project/backend | [`codecov.yml`](../codecov.yml) | After backend coverage upload | Configured (`target: auto`, `threshold: 0%`) but **not** a required status check on `main` yet — maintainers may add later |
| Release / Quay image builds | `release.yml`, `build-push-quay.yml` | Tags / release paths | Not PR merge gates |

## Complementary coverage gates (not GitHub required contexts)

| Gate | Where | Notes |
|------|-------|-------|
| Codecov project + patch (backend flag) | `codecov.yml` + `pr-checks` upload | Upload uses GitHub App OIDC (`use_oidc: true`) with `CODECOV_TOKEN` fallback |
| Maven JaCoCo service-root package floor | `backend/pom.xml` (`jacoco-check`) | Local/`mvn verify` floor for PACKAGE `com.redhat.migrationtoolkit.rhcl.service` (#198 / PR #253). Complements Codecov; **not** a branch-protection context |

## Change-type guidance

| Change type | What to expect |
|-------------|----------------|
| Backend Java | All required checks; watch Checkstyle, PMD, Backend tests & coverage, `codecov/patch/backend` |
| Frontend | All required checks; Frontend quality is the primary local gate (`npm run typecheck` / `npm test` / `npm run build`) |
| Docs-only / markdown | Full `pr-checks` still runs today (no path filter); Gitleaks + traceability still apply |
| Locale / i18n strings | All of the above **plus** Validate en/ja locale parity when path filters match |

## Refresh procedure

When adding or renaming a CI job:

1. Update the workflow `name:` / job `name:` (display name).
2. Update this matrix.
3. If the check should block merges, add it to `main` branch protection (maintainer) and mention it in [CONTRIBUTING.md](../CONTRIBUTING.md).
