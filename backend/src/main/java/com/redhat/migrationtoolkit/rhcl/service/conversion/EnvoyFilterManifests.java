package com.redhat.migrationtoolkit.rhcl.service.conversion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map-based EnvoyFilter envelope builders for dynamic config patch content (#262 phase 2).
 */
public final class EnvoyFilterManifests {

    private EnvoyFilterManifests() {
    }

    public static Map<String, Object> baseDocument(
            String appLabel,
            String namespace,
            String resourceName,
            boolean includeMigratedFromLabel) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", resourceName);
        metadata.put("namespace", namespace);
        metadata.put("labels", IstioManifestSupport.baseLabels(appLabel, includeMigratedFromLabel));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("apiVersion", "networking.istio.io/v1alpha3");
        document.put("kind", "EnvoyFilter");
        document.put("metadata", metadata);
        return document;
    }

    public static Map<String, Object> gatewayWorkloadSpec(String serviceKebabName) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workloadSelector", Map.of(
                "labels", IstioManifestSupport.gatewayWorkloadLabels(serviceKebabName)));
        return spec;
    }

    public static Map<String, Object> httpRouteGatewayPatch(Map<String, Object> patchValue) {
        Map<String, Object> configPatch = new LinkedHashMap<>();
        configPatch.put("applyTo", "HTTP_ROUTE");
        configPatch.put("match", Map.of("context", "GATEWAY"));
        configPatch.put("patch", Map.of("operation", "MERGE", "value", patchValue));
        return configPatch;
    }

    public static Map<String, Object> networkFilterPatch(String context, Map<String, Object> patchValue) {
        Map<String, Object> filter = Map.of("name", "envoy.filters.network.http_connection_manager");
        Map<String, Object> filterChain = Map.of("filter", filter);
        Map<String, Object> listener = Map.of("filterChain", filterChain);
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("context", context);
        match.put("listener", listener);

        Map<String, Object> configPatch = new LinkedHashMap<>();
        configPatch.put("applyTo", "NETWORK_FILTER");
        configPatch.put("match", match);
        configPatch.put("patch", Map.of("operation", "MERGE", "value", patchValue));
        return configPatch;
    }

    public static Map<String, Object> httpFilterGatewayPatch(String operation, Map<String, Object> patchValue) {
        Map<String, Object> subFilter = Map.of("name", "envoy.filters.http.router");
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("name", "envoy.filters.network.http_connection_manager");
        filter.put("subFilter", subFilter);
        Map<String, Object> filterChain = Map.of("filter", filter);
        Map<String, Object> listener = Map.of("filterChain", filterChain);
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("context", "GATEWAY");
        match.put("listener", listener);

        Map<String, Object> configPatch = new LinkedHashMap<>();
        configPatch.put("applyTo", "HTTP_FILTER");
        configPatch.put("match", match);
        configPatch.put("patch", Map.of("operation", operation, "value", patchValue));
        return configPatch;
    }

    public static void withConfigPatches(Map<String, Object> spec, List<Map<String, Object>> configPatches) {
        spec.put("configPatches", configPatches);
    }
}
