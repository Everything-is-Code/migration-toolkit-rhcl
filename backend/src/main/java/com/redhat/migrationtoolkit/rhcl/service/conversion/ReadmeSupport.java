package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport.PlanCeiling;

import java.util.List;
import java.util.stream.Collectors;

/**
 * README.md assembly (#170) — kept out of {@link ConversionService} orchestrator.
 */
public final class ReadmeSupport {

    private ReadmeSupport() {
    }

    public static String build(ConversionContext ctx,
                               ConversionService.ReadmeNotes notes,
                               PolicyFinder policyFinder,
                               PolicyConfigSupport policyConfigSupport,
                               RateLimitSupport rateLimitSupport) {
        if (notes == null) {
            notes = new ConversionService.ReadmeNotes();
        }
        ApiService service = ctx.service;
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        BackendType backendType = ctx.primaryBackendType;
        String externalHost = ctx.primaryExternalHost;
        List<ResolvedBackend> backends = ctx.resolvedBackends;
        boolean overrideIgnored = ctx.overrideIgnored;
        boolean includeTlsPolicy = ctx.options.includeTlsPolicy;
        boolean includeDnsPolicy = ctx.emitDnsPolicy();

        List<String> externalHosts = backends == null ? List.of() : backends.stream()
                .filter(b -> b.type == BackendType.EXTERNAL && b.externalHost != null)
                .map(b -> b.externalHost)
                .distinct()
                .toList();
        String externalEndpointDisplay;
        if (externalHosts.size() > 1) {
            externalEndpointDisplay = externalHosts.stream()
                    .map(h -> "`" + h + "`")
                    .collect(Collectors.joining(", "))
                    + " — first listed is the historical **primary (first)** host only";
        } else if (externalHosts.size() == 1) {
            externalEndpointDisplay = "`" + externalHosts.get(0) + "`";
        } else {
            externalEndpointDisplay = "`" + (externalHost != null ? externalHost : "") + "`";
        }
        String hostRewriteNote = externalHosts.size() > 1
                ? """

> **Note:** `URLRewrite` Host is emitted only when a rule's selected backends share **one** external
> hostname. Rules that load-balance across distinct hosts omit Host rewrite (Gateway API is rule-scoped).
"""
                : "";

        String backendSection = switch (backendType) {
            case EXTERNAL -> """

## External Backend (External HTTPS Service)

The backend is an HTTPS endpoint outside the cluster.

**External endpoint(s):** %s
%s
| File | Description |
|------|-------------|
| serviceentry.yaml | Register the external host with Istio (ServiceEntry + ExternalName Service) |
| destinationrule.yaml | Apply TLS (SIMPLE) for connections to the external host |
| httproute.yaml | Rewrite the Host header to the external hostname via `URLRewrite` (when unique) |
""".formatted(externalEndpointDisplay, hostRewriteNote);
            case INTERNAL -> """

## Internal Backend (Service within OpenShift)

The backend is a Kubernetes Service within the cluster.
ServiceEntry, DestinationRule, and URLRewrite filters are not needed and have not been generated.

> Verify that `backendRefs.name` in `httproute.yaml` matches the actual Service name.
""";
        };

        boolean multiBackend = backends != null && backends.size() > 1;
        String multiBackendNotes = "";
        if (multiBackend) {
            String mounts = backends.stream()
                    .map(b -> "- `" + b.mountPath + "` → `" + b.refName + "`"
                            + (b.privateEndpoint != null ? " (" + b.privateEndpoint + ")" : ""))
                    .collect(Collectors.joining("\n"));
            multiBackendNotes = """

## Multiple backends (path-first)

This product has %d backends. HTTPRoute rules select `backendRefs` by longest mount-path prefix match.
Equal mounts (including blank/`/`) share weighted `backendRefs`. AuthPolicy and RateLimitPolicy still
target the single HTTPRoute `%s-route`.
If a mapping-rule path matches **no** mount, conversion falls back to **all** backends (logged as a warning).

%s
""".formatted(backends.size(), name, mounts);
            if (overrideIgnored) {
                multiBackendNotes += """

> **Note:** `externalBackendUrl` override was **ignored** because more than one backend is present.
> Routing stays path-based across all backends.
""";
            }
        }

        boolean hasLogging = policyFinder.findEnabledExact(service, "logging") != null;
        String loggingFile = hasLogging
                ? "| gateway.yaml | Gateway + Istio Telemetry / EnvoyFilter (access log configuration) |\n" : "";

        boolean hasUrlRewriting = policyFinder.findEnabledExact(service, "url_rewriting") != null;
        String urlRewritingFile = hasUrlRewriting
                ? "| envoyfilter-url-rewriting.yaml | Reproduces the 3scale URL Rewriting policy via Lua filter (PCRE→Lua pattern conversion is best-effort — verify before use) |\n"
                : "";

        String tlsFile = includeTlsPolicy
                ? "| tlspolicy.yaml | Kuadrant TLSPolicy (cert-manager issuerRef on Gateway) |\n"
                : "";
        String dnsFile = includeDnsPolicy
                ? "| dnspolicy.yaml | Kuadrant DNSPolicy targeting the Gateway |\n"
                : "";
        Policy contentLimits = policyFinder.findEnabledAny(service, true, "content_limits", "payload_limits");
        Integer requestLimit = contentLimits != null
                ? policyConfigSupport.resolveContentLimitBytes(contentLimits, true) : null;
        String contentLimitsFile = (requestLimit != null && requestLimit > 0)
                ? "| envoyfilter-content-limits.yaml | Envoy buffer filter enforcing request body byte limit from 3scale content_limits |\n"
                : "";

        String fileList = loggingFile
                + urlRewritingFile
                + tlsFile
                + dnsFile
                + contentLimitsFile
                + (backendType == BackendType.EXTERNAL
                ? "| serviceentry.yaml | Istio ServiceEntry + ExternalName Service for external backend |\n"
                + "| destinationrule.yaml | TLS origination to external host |"
                : "");

        Policy tokenIntrospection = policyFinder.findEnabled(service, "token_introspection");
        String tokenIntrospectionNotes = "";
        if (tokenIntrospection != null) {
            java.util.Map<String, Object> cfg = tokenIntrospection.configuration != null
                    ? tokenIntrospection.configuration : java.util.Map.of();
            String endpoint = AuthPolicySupport.firstNonBlank(
                    cfg.get("introspection_url"),
                    cfg.get("introspectionEndpoint"),
                    cfg.get("endpoint"));
            if (endpoint == null) {
                tokenIntrospectionNotes = """

## WARNING: Incomplete token_introspection

The 3scale `token_introspection` policy is present but missing `introspection_url`.
AuthPolicy oauth2Introspection was **not** fully generated — do not claim full support until the
introspection endpoint and client credentials are configured.
""";
            } else {
                tokenIntrospectionNotes = """

## OAuth 2.0 Token Introspection

`policy.yaml` uses AuthPolicy `oauth2Introspection` (endpoint + credentialsRef).
Confirm `secret.yaml` (`%s-oauth2-introspection`) clientID/clientSecret before apply.
""".formatted(name);
            }
        }

        notes.add(tokenIntrospectionNotes);
        notes.add(buildRateLimitApproximationNotes(service, policyFinder, rateLimitSupport));
        notes.add(JwtClaimCheckSupport.buildReadmeNotes(service, policyFinder));
        notes.add(buildContentLimitsReadmeNotes(service, policyFinder, policyConfigSupport));
        String dynamicNotes = String.join("", notes.all());

        return """
# %s - Connectivity Link Migration

## Overview
Kubernetes/OpenShift resources generated by Migration Toolkit.

**Original 3scale service:** %s (ID: %s)
**Target Namespace:** %s
**Backend type:** %s
%s%s
## Files

| File | Description |
|------|-------------|
| gateway.yaml | Gateway serving as the entry point for external traffic |
| httproute.yaml | HTTPRoute converted from 3scale mapping rules |
| policy.yaml | Authentication/authorization policy (AuthPolicy) |
| secret.yaml | Credentials (replace values before applying) |
| configmap.yaml | Configuration data |
%s
%s
## Prerequisites
- OpenShift with Connectivity Link (Kuadrant) operator
- Gateway API CRDs
- Istio

## Installation

```bash
# Review and update the values in secret.yaml before applying
vi secret.yaml
kubectl apply -f . -n %s

# Verify Gateway
kubectl get gateway %s-gateway -n %s
kubectl get httproute %s-route -n %s
```

## Notes
- Make sure to update the credentials in `secret.yaml` before applying
- Verify that the backend service name in `httproute.yaml` matches the actual Service name
- AuthPolicy / RateLimitPolicy target the single HTTPRoute (`%s-route`)
- Test in a staging environment first
""".formatted(
                service.name, service.name, service.id, namespace,
                backendType == BackendType.EXTERNAL ? "External HTTPS" : "Internal OpenShift Service",
                backendSection,
                multiBackendNotes,
                fileList,
                dynamicNotes,
                namespace, name, namespace, name, namespace, name
        );
    }

    private static String buildRateLimitApproximationNotes(ApiService service,
                                                           PolicyFinder policyFinder,
                                                           RateLimitSupport rateLimitSupport) {
        boolean hasLeaky = false;
        boolean hasConn = false;
        Policy edge = policyFinder.findEnabled(service, "edge_limiting");
        if (edge != null && edge.configuration != null) {
            Object leaky = edge.configuration.get("leaky_bucket_limiters");
            if (leaky instanceof List<?> list && !list.isEmpty()) {
                hasLeaky = true;
            }
            Object conn = edge.configuration.get("connection_limiters");
            if (conn instanceof List<?> list && !list.isEmpty()) {
                hasConn = true;
            }
        }
        PlanCeiling ceiling = rateLimitSupport.resolvePlanCeiling(service);
        boolean hasPlanCeiling = ceiling != null;
        if (!hasLeaky && !hasConn && !hasPlanCeiling) {
            return "";
        }

        StringBuilder bullets = new StringBuilder();
        if (hasConn) {
            bullets.append(
                    "- **connection_limiters → rate**: concurrent connections are approximated as a "
                            + "per-second rate ceiling (`window: 1s`); connection semantics are not preserved\n");
        }
        if (hasLeaky) {
            bullets.append(
                    "- **leaky_bucket → fixed window**: leaky-bucket limiters are emitted as fixed "
                            + "`window: 1s` rates; not true leaky-bucket semantics\n");
        }
        if (hasPlanCeiling) {
            bullets.append(
                    "- **plan ceiling**: `global` limit is the **max** across all application plans "
                            + "(not a per-plan ceiling)\n");
        }
        return """

## WARNING: Rate-limit approximations

`ratelimitpolicy.yaml` includes best-effort mappings from 3scale. Review before apply:

%s""".formatted(bullets);
    }

    private static String buildContentLimitsReadmeNotes(ApiService service,
                                                        PolicyFinder policyFinder,
                                                        PolicyConfigSupport policyConfigSupport) {
        Policy contentLimits = policyFinder.findEnabledAny(service, true, "content_limits", "payload_limits");
        if (contentLimits == null) {
            return "";
        }
        Integer responseBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, false);
        if (responseBytes == null || responseBytes <= 0) {
            Integer requestBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, true);
            if (requestBytes != null && requestBytes > 0) {
                return """

## Response/Request Content Limits

`envoyfilter-content-limits.yaml` enforces the request body byte limit via Envoy buffer filter.
""";
            }
            return "";
        }
        return """

## WARNING: Response content limit not enforced

3scale `content_limits` response / `response_content_limit` (%d bytes) is recorded on the HTTPRoute
annotation `3scale-migration/response-content-limit` but is **not** hard-enforced in Envoy.
Gateway API / Istio has no portable response-body size filter in this converter — verify manually
if response size must be capped.
""".formatted(responseBytes);
    }
}
