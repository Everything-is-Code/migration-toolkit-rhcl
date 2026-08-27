# Playwright E2E (live 3scale)

UI flow tests in TypeScript. Requires a running backend (`localhost:8080`) and frontend (`localhost:5173`).

Used by **`/verify`** in the SDD workflow when the change touches `frontend/`.

## Setup

```bash
cd frontend
npm install
npx playwright install chromium
cp e2e/.env.example e2e/.env.local
# edit e2e/.env.local with lab credentials (never commit)
```

## Run

```bash
# back + front already running:
export E2E_SKIP_WEBSERVER=true
export THREESCALE_ADMIN_URL=...
export THREESCALE_ACCESS_TOKEN=...
npm run test:e2e

# or wrapper (exit 2 = skipped when env missing):
bash scripts/run-e2e-if-configured.sh
```

## YAML verification

| File | Role |
|------|------|
| `e2e/yaml-expectations.ts` | Per `rhcl_seed_*` product: required fragments per YAML tab |
| `e2e/yaml-assertions.ts` | Asserts every tab listed for a product |
| `e2e/migration-workflow.spec.ts` | One test per seed product + #229 switch-API regression |

When adding a conversion seed case, extend **`yaml-expectations.ts`** in the same change.

## Record a new flow

```bash
npm run test:e2e:codegen
```

Refactor into `helpers.ts` + expectations; do not commit tokens.

## SDD integration

**`/verify-fe`** (SDD skill) — asks for Admin URL + PAT in chat, runs E2E with session-only env vars. Writes `verify-fe-report.md`.

**`/verify`** — unit tests only; expects `verify-fe-report.md` when `frontend/` changed.
