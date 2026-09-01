# Conversion Architecture — Implemented Design (#40)

> **Sync note:** This file is mirrored in the SDD store at  
> [rhcl-sdd/docs/conversion-architecture.md](https://github.com/Everything-is-Code/rhcl-sdd/blob/main/docs/conversion-architecture.md).  
> When updating conversion design, keep both copies aligned (SDD is canonical for OpenSpec; this copy is for product-repo readers).

`ConversionService` (~175 lines) orchestrates conversion: it builds a `ConversionContext` once per call and delegates YAML generation to a **registry of resource generators**. Policy-specific logic lives in generators and contributors under `service/generator/`, not in the orchestrator.

OpenSpec change: `conversion-strategy-registry` (GitHub [#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40)).

## Level 1 — Strategy + Registry (per output file)

Each output file is produced by one `ResourceGenerator` bean, collected by `ResourceGeneratorRegistry` (CDI `Instance<ResourceGenerator>`). `ConversionService.convert()` no longer branches on file names.

| Output file | Generator | When emitted |
|-------------|-----------|--------------|
| `gateway.yaml` | `GatewayGenerator` | Always |
| `httproute.yaml` | `HttpRouteGenerator` | Always |
| `policy.yaml` | `AuthPolicyGenerator` | Always (AuthPolicy YAML) |
| `secret.yaml` | `SecretGenerator` | Always |
| `configmap.yaml` | `ConfigMapGenerator` | Always |
| `apiproduct.yaml` | `ApiProductGenerator` | Always |
| `README.md` | `ReadmeGenerator` | Always |
| `apikey.yaml` | `ApiKeyGenerator` | `authentication.type == apiKey` |
| `serviceentry.yaml` | `ServiceEntryGenerator` | External backend(s) |
| `destinationrule.yaml` | `DestinationRuleGenerator` | External backend(s) |
| `telemetry.yaml` | `TelemetryGenerator` | `logging` policy enabled |
| `envoyfilter-logging.yaml` | `LoggingEnvoyFilterGenerator` | Logging policy with non-empty `json_object_config` |
| `envoyfilter-url-rewriting.yaml` | `UrlRewritingEnvoyFilterGenerator` | `url_rewriting` with commands |
| `envoyfilter-content-limits.yaml` | `ContentLimitsEnvoyFilterGenerator` | Request body limit bytes > 0 |
| `envoyfilter-retry.yaml` | `RetryEnvoyFilterGenerator` | Retry policy when `!options.retriesSupported` |
| `authorizationpolicy.yaml` | `AuthorizationPolicyGenerator` | `ip_check` + `ipCheckMode == authorizationPolicy` |
| `ratelimitpolicy.yaml` | `RateLimitPolicyGenerator` | Edge limiting produces YAML |
| `tlspolicy.yaml` | `TlsPolicyGenerator` | `options.includeTlsPolicy` |
| `dnspolicy.yaml` | `DnsPolicyGenerator` | `ConversionContext.emitDnsPolicy(options)` |

**Discovery test:** `ResourceGeneratorRegistryDiscoveryTest` — new `@ApplicationScoped` generator picked up without editing the registry.

## Level 2 — Collector / Contributor (multi-policy files)

Complex files aggregate fragments from unrelated 3scale policies via CDI `Instance<*Contributor>` and a shared builder:

| Output file | Builder | Contributors (examples) |
|-------------|---------|-------------------------|
| `httproute.yaml` | `HttpRouteBuilder` | Mapping rules, header mod, CORS, timeouts, retry, annotations |
| `policy.yaml` | `AuthPolicyBuilder` | Anonymous, OAuth2 introspection, JWT, API key, IP OPA, JWT claim check, Keycloak roles, … |
| `secret.yaml` | `SecretBuilder` | Anonymous/default credentials, token introspection, App ID/key, API key, default JWT credentials |

Contributor order uses `@Priority` resolved via `ContributorOrdering` (reads annotation on CDI proxy superclass).

**Discovery tests:** `HttpRouteContributorDiscoveryTest`, `AuthPolicyContributorDiscoveryTest`, `SecretContributorDiscoveryTest`.

## Shared spine

| Component | Role |
|-----------|------|
| `PolicyFinder` | Central enabled-policy lookup (replaces per-policy `find*Policy` in orchestrator) |
| `ConversionContext` | Per-convert snapshot: service, namespace, backends, options |
| `BackendResolver` / `ResolvedBackend` | Multi-backend and external URL resolution |
| `ConversionYamlSupport` | YAML normalization, label stripping |
| `ReadmeSupport` + `ReadmeNotes` | README assembly (#170 — collector, not positional note args) |
| `service/conversion/*Support` | Policy-specific helpers shared by contributors |

## Before adding a new policy conversion

1. **#149** — epic for recognized-but-unconverted policies (each with issue + spec).
2. **#40** — add a new `*Contributor` (and generator only if a new output file is needed); do **not** extend `ConversionService`.
3. **#170** — extend `ReadmeSupport` / `ReadmeNotes`; do not add positional parameters to orchestrator APIs.

## Adapter integration

YAML generation is native in this repo (Kuadrant / Gateway API / Istio). The external **from-3scale-to-connectivity-link** adapter is a design reference only — not a runtime dependency.

## Testing

- `ConversionServiceTest` — YAML string assertions are whitespace-sensitive.
- `ConversionServiceConcurrencyTest` — parallel `convert()` safety.
- `ArchitectureTest` — layering; contributors must not depend on `ConversionService`.
- `*DiscoveryTest` — CDI auto-discovery for registry and each contributor family.
- **Windows CRLF:** local `mvn test` may fail on YAML whitespace with `core.autocrlf=true` while Linux CI is green. Trust CI before treating as regression.
