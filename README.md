# Migration Toolkit for Red Hat Connectivity Link

A single-app GUI for migrating **3scale → Red Hat Connectivity Link** (Kuadrant / Gateway API / Istio).  
Quarkus backend + React/PatternFly frontend: export, compatibility check, YAML conversion, validation, apply, and history — all in one repository.

---

## Screenshots

| Connection | Compatibility |
|:-:|:-:|
| ![Connect](.claude/images/connect.png) | ![API list](.claude/images/apilist.png) |

| YAML generation | Preview |
|:-:|:-:|
| ![YAML](.claude/images/yaml.png) | ![Preview](.claude/images/preview.png) |

More screenshots and feature detail: **[documentation/user-guide.md](documentation/user-guide.md)**.

---

## Quick start

### Helm (preferred)

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
oc -n migration-toolkit get route
```

Images: `quay.io/everythingascode/migration-toolkit-rhcl-{backend,frontend}:latest`

Full options (local chart, GitOps, OpenShift S2I): **[documentation/deployment.md](documentation/deployment.md)**

### Local development

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Node.js | 22 |
| PostgreSQL | localhost:5432 |

```bash
cd backend && mvn quarkus:dev

cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

Setup, tests, PR workflow: **[CONTRIBUTING.md](CONTRIBUTING.md)**

---

## Documentation

| Document | Description |
|----------|-------------|
| **[documentation/](documentation/README.md)** | User guide, API, data model, deployment, **conversion architecture** |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Dev setup and tests |
| [AGENTS.md](AGENTS.md) | Repo map for AI contributors |
| [SECURITY.md](SECURITY.md) | Report vulnerabilities |
| [docs/README.ja.md](docs/README.ja.md) | Japanese README (legacy mirror) |

**Conversion architecture** lives in both this repo ([`documentation/conversion-architecture.md`](documentation/conversion-architecture.md)) and the [rhcl-sdd store](https://github.com/Everything-is-Code/rhcl-sdd/blob/main/docs/conversion-architecture.md) — keep them in sync when extending policy conversion ([#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40)).

---

## Testing (quick)

```bash
cd backend && mvn verify
cd frontend && npm run typecheck && npm test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for coverage, reports, and CI context.

---

Maintained by [Everything-is-Code/migration-toolkit-rhcl](https://github.com/Everything-is-Code/migration-toolkit-rhcl) — [CODEOWNERS](.github/CODEOWNERS).
