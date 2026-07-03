package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityItem;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CompatibilityService {

    private static final int SCORE_SUPPORTED = 20;
    private static final int SCORE_WARNING = 10;
    private static final int SCORE_UNSUPPORTED = 0;

    // Mapping from 3scale policy system name → display name shown in the UI
    private static final Map<String, String> POLICY_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("apicast",                    "3scale APIcast"),
        Map.entry("3scale_batcher",             "3scale Batcher"),
        Map.entry("auth_caching",               "3scale Auth Caching"),
        Map.entry("referrer",                   "3scale Referrer"),
        Map.entry("anonymous_access",           "Anonymous Access"),
        Map.entry("default_credentials",        "Anonymous Access"),
        Map.entry("camel",                      "Camel Service"),
        Map.entry("conditional",                "Conditional Policy"),
        Map.entry("content_caching",            "Content Caching"),
        Map.entry("cors",                       "CORS Request Handling"),
        Map.entry("custom_metrics",             "Custom metrics"),
        Map.entry("echo",                       "Echo"),
        Map.entry("edge_limiting",              "Edge Limiting"),
        Map.entry("header_modification",        "Header Modification"),
        Map.entry("headers",                    "Header Modification"),
        Map.entry("ip_check",                   "IP Check"),
        Map.entry("jwt_claim_check",            "JWT Claim Check"),
        Map.entry("liquid_context_debug",       "Liquid Context Debug"),
        Map.entry("logging",                    "Logging"),
        Map.entry("maintenance_mode",           "Maintenance Mode"),
        Map.entry("oauth2_mutual_tls",          "OAuth 2.0 Mutual TLS Client Authentication"),
        Map.entry("token_introspection",        "OAuth 2.0 Token Introspection"),
        Map.entry("proxy_service",              "Proxy Service"),
        Map.entry("rate_limit_headers",         "Rate Limit Headers"),
        Map.entry("content_limits",             "Response/Request Content Limits"),
        Map.entry("retry",                      "Retry"),
        Map.entry("keycloak_role_check",        "RH-SSO/Keycloak Role Check"),
        Map.entry("routing",                    "Routing"),
        Map.entry("soap",                       "SOAP"),
        Map.entry("tls_validation",             "TLS Client Certificate Validation"),
        Map.entry("tls_termination",            "TLS Termination"),
        Map.entry("upstream",                   "Upstream"),
        Map.entry("upstream_connection",        "Upstream Connection"),
        Map.entry("upstream_mtls",              "Upstream Mutual TLS"),
        Map.entry("url_rewriting",              "URL Rewriting"),
        Map.entry("rewrite",                    "URL Rewriting with Captures"),
        Map.entry("url_rewriting_captures",     "URL Rewriting with Captures")
    );

    public CompatibilityResult check(ApiService service, Set<String> supportedPolicies) {
        CompatibilityResult result = new CompatibilityResult();
        result.serviceId = service.id;
        result.serviceName = service.name;
        result.items = new ArrayList<>();

        checkAuthentication(service, result.items);
        checkPolicies(service, result.items, supportedPolicies);
        checkMappingRules(service, result.items);
        checkBackend(service, result.items);

        result.score = calculateScore(result.items);
        result.level = scoreToLevel(result.score);
        return result;
    }

    private void checkAuthentication(ApiService service, List<CompatibilityItem> items) {
        if (service.authentication == null) {
            items.add(new CompatibilityItem("Authentication", "WARNING", "No authentication configured"));
            return;
        }
        switch (service.authentication.type) {
            case "jwt" ->
                items.add(new CompatibilityItem(
                        "JWT Authentication", "SUPPORTED", "JWT/OIDC is fully supported"));
            case "apiKey" ->
                items.add(new CompatibilityItem(
                        "API Key Authentication", "SUPPORTED", "API key authentication is supported"));
            case "appIdKey" ->
                items.add(new CompatibilityItem(
                        "App ID/Key Authentication", "WARNING",
                        "App ID/Key requires custom policy configuration"));
            default ->
                items.add(new CompatibilityItem(
                        "Authentication", "WARNING",
                        "Authentication type may require manual review"));
        }
    }

    private void checkPolicies(ApiService service, List<CompatibilityItem> items, Set<String> supportedPolicies) {
        if (service.policies == null || service.policies.isEmpty()) {
            return;
        }
        for (Policy policy : service.policies) {
            if (Boolean.FALSE.equals(policy.enabled)) {
                continue;
            }
            String systemName = policy.name != null ? policy.name.toLowerCase() : "";
            String displayName = POLICY_DISPLAY_NAMES.getOrDefault(systemName, policy.name);

            // Logging ポリシーで enable_json_logs=true かつ json_object_config に
            // 1件以上設定がある場合、対応ポリシーリストに含まれていても WARNING にする。
            // Envoy 変数へのマッピング内容を目視確認してほしいため。
            if ("logging".equals(systemName) && hasJsonObjectConfig(policy)) {
                items.add(new CompatibilityItem(displayName, "WARNING",
                        "JSON log format (json_object_config) will be mapped to Istio Telemetry format.labels"
                        + " — please verify the generated telemetry.yaml"));
                continue;
            }

            if (supportedPolicies.contains(displayName)) {
                items.add(new CompatibilityItem(displayName, "SUPPORTED",
                        "Policy is in the supported policy list"));
            } else {
                items.add(new CompatibilityItem(displayName, "WARNING",
                        "Policy is not in the supported policy list — manual review required"));
            }
        }
    }

    private boolean hasJsonObjectConfig(Policy policy) {
        if (policy.configuration == null) {
            return false;
        }
        Object raw = policy.configuration.get("json_object_config");
        if (raw instanceof java.util.List<?> list) {
            return !list.isEmpty();
        }
        if (raw instanceof String str) {
            return !str.isBlank() && !str.equals("[]");
        }
        return false;
    }

    private void checkMappingRules(ApiService service, List<CompatibilityItem> items) {
        if (service.mappingRules == null || service.mappingRules.isEmpty()) {
            items.add(new CompatibilityItem("Mapping Rules", "WARNING", "No mapping rules found"));
            return;
        }
        boolean hasWildcard = service.mappingRules.stream()
                .anyMatch(r -> r.pattern != null && r.pattern.contains("{?}"));
        if (hasWildcard) {
            items.add(new CompatibilityItem(
                    "Mapping Rules", "WARNING",
                    "Wildcard patterns may need adjustment for HTTPRoute"));
        } else {
            items.add(new CompatibilityItem(
                    "Mapping Rules", "SUPPORTED",
                    service.mappingRules.size() + " mapping rules will be converted to HTTPRoute rules"));
        }
    }

    private void checkBackend(ApiService service, List<CompatibilityItem> items) {
        if (service.backends == null || service.backends.isEmpty()) {
            items.add(new CompatibilityItem("Backend", "WARNING", "No backend configured"));
            return;
        }
        for (Backend backend : service.backends) {
            if (backend.privateEndpoint != null && backend.privateEndpoint.startsWith("https://")) {
                items.add(new CompatibilityItem(
                        "Backend TLS", "SUPPORTED", "HTTPS backend endpoint supported"));
            } else {
                items.add(new CompatibilityItem(
                        "Backend: " + backend.name, "SUPPORTED",
                        "Backend URL will be configured in HTTPRoute"));
            }
        }
    }

    private int calculateScore(List<CompatibilityItem> items) {
        if (items.isEmpty()) {
            return 100;
        }
        int total = 0;
        int max = items.size() * SCORE_SUPPORTED;
        for (CompatibilityItem item : items) {
            total += switch (item.status) {
                case "SUPPORTED" -> SCORE_SUPPORTED;
                case "WARNING" -> SCORE_WARNING;
                default -> SCORE_UNSUPPORTED;
            };
        }
        return (int) ((double) total / max * 100);
    }

    private String scoreToLevel(int score) {
        if (score >= 80) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
