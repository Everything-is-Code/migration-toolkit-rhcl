package com.redhat.migrationtoolkit.rhcl.service.conversion;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for typed Istio manifest generators (#262 phase 2).
 */
public final class IstioManifestSupport {

    private IstioManifestSupport() {
    }

    public static ManifestSerializer resolveSerializer(ManifestSerializer injected) {
        return injected != null ? injected : new ManifestSerializer();
    }

    public static Map<String, String> baseLabels(String appLabel, boolean includeMigratedFromLabel) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", appLabel);
        if (includeMigratedFromLabel) {
            labels.put("migrated-from", "3scale");
        }
        return labels;
    }

    public static Map<String, String> gatewayWorkloadLabels(String serviceKebabName) {
        return Map.of("gateway.networking.k8s.io/gateway-name", serviceKebabName + "-gateway");
    }

    public static Map<String, String> loggingWorkloadLabels(String serviceKebabName, boolean gateway) {
        if (gateway) {
            return gatewayWorkloadLabels(serviceKebabName);
        }
        return Map.of("app", serviceKebabName);
    }

    public static String joinDocuments(ManifestSerializer serializer, HasMetadata... documents) {
        String[] chunks = new String[documents.length];
        for (int i = 0; i < documents.length; i++) {
            chunks[i] = serializer.toYaml(documents[i]);
        }
        return joinYamlChunks(chunks);
    }

    public static String joinYamlChunks(String... yamlChunks) {
        StringBuilder joined = new StringBuilder();
        for (String chunk : yamlChunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            String trimmed = chunk.stripTrailing();
            if (joined.length() > 0) {
                if (joined.charAt(joined.length() - 1) != '\n') {
                    joined.append('\n');
                }
                joined.append("---\n");
            }
            joined.append(trimmed);
        }
        if (joined.length() > 0 && joined.charAt(joined.length() - 1) != '\n') {
            joined.append('\n');
        }
        return joined.toString();
    }
}
