package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.Policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared builders for {@code service/conversion/} unit tests. */
final class ConversionSupportTestFixtures {

    private ConversionSupportTestFixtures() {
    }

    static ApiService apiService(String name) {
        ApiService service = new ApiService();
        service.id = "1";
        service.name = name;
        service.systemName = name;
        service.backends = new ArrayList<>();
        service.policies = new ArrayList<>();
        service.mappingRules = new ArrayList<>();
        service.applicationPlans = new ArrayList<>();
        return service;
    }

    static Backend backend(String name, String endpoint, String mountPath) {
        Backend backend = new Backend();
        backend.name = name;
        backend.systemName = name;
        backend.privateEndpoint = endpoint;
        backend.path = mountPath;
        return backend;
    }

    static Backend backend(String name, String endpoint, String mountPath, Integer weight) {
        Backend backend = backend(name, endpoint, mountPath);
        backend.weight = weight;
        return backend;
    }

    static ConversionOptions conversionOptions() {
        return new ConversionOptions();
    }

    static ConversionContext context(ApiService service, String namespace) {
        return ConversionContext.build(service, namespace, null, conversionOptions(), new BackendResolver());
    }

    static ConversionContext context(ApiService service, String namespace, String backendUrl,
            ConversionOptions options) {
        return ConversionContext.build(service, namespace, backendUrl, options, new BackendResolver());
    }

    static Policy policy(String name, boolean enabled, Map<String, Object> configuration) {
        Policy policy = new Policy();
        policy.name = name;
        policy.enabled = enabled;
        policy.configuration = configuration != null ? configuration : new HashMap<>();
        return policy;
    }

    static ResolvedBackend resolvedBackend(BackendType type, String refName, String mountPath) {
        return new ResolvedBackend(type, refName, refName + "-se", refName + "-dr",
                type == BackendType.EXTERNAL ? "api.example.com" : null,
                8080, false, mountPath, 1, null);
    }

    static ApiService richService() {
        ApiService service = apiService("demo-api");
        service.authentication = new com.redhat.migrationtoolkit.rhcl.model.Authentication();
        service.authentication.type = "jwt";
        service.backends.add(backend("primary", "https://api.example.com", "/"));
        service.backends.add(backend("secondary", "https://api2.example.com", "/v2", 50));
        service.mappingRules.add(mappingRule("GET", "/users/{id}"));
        service.policies.add(edgeLimitingPolicy(100, 60));
        service.policies.add(contentLimitsPolicy(2048, 4096));
        service.policies.add(jwtClaimCheckPolicy(List.of(
                Map.of("jwt_claim", "sub", "op", "==", "value", "user-a"))));
        service.policies.add(tokenIntrospectionPolicy("https://idp.example.com/introspect"));
        service.applicationPlans.add(planWithLimits(
                Map.of("period", "minute", "value", 120),
                Map.of("period", "hour", "value", 5000)));
        return service;
    }

    static com.redhat.migrationtoolkit.rhcl.model.MappingRule mappingRule(String method, String pattern) {
        com.redhat.migrationtoolkit.rhcl.model.MappingRule rule =
                new com.redhat.migrationtoolkit.rhcl.model.MappingRule();
        rule.httpMethod = method;
        rule.pattern = pattern;
        return rule;
    }

    static ApplicationPlan planWithLimits(Map<String, Object>... limits) {
        ApplicationPlan plan = new ApplicationPlan();
        plan.limits = List.of(limits);
        return plan;
    }

    static Policy edgeLimitingPolicy(int count, int windowSeconds) {
        return policy("edge_limiting", true, Map.of(
                "fixed_window_limiters", List.of(Map.of(
                        "count", count,
                        "window", windowSeconds,
                        "key", Map.of("name", "service"))),
                "leaky_bucket_limiters", List.of(Map.of("rate", 10)),
                "connection_limiters", List.of(Map.of("conn", 5))));
    }

    static Policy contentLimitsPolicy(int requestBytes, int responseBytes) {
        return policy("content_limits", true, Map.of(
                "request", requestBytes,
                "response", responseBytes));
    }

    static Policy jwtClaimCheckPolicy(List<Map<String, Object>> operations) {
        return policy("jwt_claim_check", true, Map.of(
                "enable_extended_context", true,
                "rules", List.of(Map.of(
                        "combine_op", "and",
                        "resource_type", "plain",
                        "resource", "/",
                        "methods", List.of("ANY"),
                        "operations", operations))));
    }

    static Policy tokenIntrospectionPolicy(String url) {
        return policy("token_introspection", true, Map.of(
                "introspection_url", url,
                "client_id", "cid",
                "client_secret", "secret"));
    }

    /** 3scale `upstream` policy with arbitrary configuration. */
    static Policy upstreamPolicy(Map<String, Object> configuration) {
        return policy("upstream", true, configuration);
    }

    /** Global catch-all upstream override (`regex: .*`). */
    static Policy upstreamGlobalCatchAll(String overrideUrl) {
        return upstreamPolicy(Map.of(
                "rules", List.of(Map.of("regex", ".*", "url", overrideUrl))));
    }

    /** Global upstream via top-level `url` with empty rules. */
    static Policy upstreamGlobalTopLevelUrl(String overrideUrl) {
        return upstreamPolicy(Map.of(
                "url", overrideUrl,
                "rules", List.of()));
    }

    /**
     * Path-scoped rules: approximable prefix/regex and optional non-approximable
     * (lookaround / possessive / backref) entries for WARNING README coverage.
     */
    static Policy upstreamPathScoped(List<?> rules) {
        return upstreamPolicy(Map.of("rules", rules));
    }

    /** Minimal service with one product backend + mapping rule for upstream convert tests. */
    static ApiService upstreamConvertService(String overrideBackendScheme) {
        ApiService service = apiService("up-api");
        service.backends.add(backend("primary", overrideBackendScheme + "://api.example.com:8080", "/"));
        service.mappingRules.add(mappingRule("GET", "/fallback"));
        return service;
    }
}
