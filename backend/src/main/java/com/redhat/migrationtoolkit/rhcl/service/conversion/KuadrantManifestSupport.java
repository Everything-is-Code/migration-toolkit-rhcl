package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;

import java.util.Map;

/**
 * Shared helpers for typed Kuadrant manifest generators (#262 phase 3).
 */
public final class KuadrantManifestSupport {

    private KuadrantManifestSupport() {
    }

    public static Map<String, String> baseLabels(String appLabel, boolean includeMigratedFromLabel) {
        return IstioManifestSupport.baseLabels(appLabel, includeMigratedFromLabel);
    }

    public static ManifestMeta meta(
            String resourceName, String namespace, String appLabel, boolean includeMigratedFromLabel) {
        return new ManifestMeta(resourceName, namespace, baseLabels(appLabel, includeMigratedFromLabel), null);
    }

    public static ManifestSerializer resolveSerializer(ManifestSerializer injected) {
        return IstioManifestSupport.resolveSerializer(injected);
    }
}
