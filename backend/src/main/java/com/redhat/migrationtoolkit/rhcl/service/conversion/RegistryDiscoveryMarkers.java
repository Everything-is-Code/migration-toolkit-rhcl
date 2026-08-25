package com.redhat.migrationtoolkit.rhcl.service.conversion;

/** Constants for registry CDI discovery tests (test bean lives under {@code src/test}). */
public final class RegistryDiscoveryMarkers {

    public static final String DISCOVERY_SYSTEM_NAME = "rhcl-registry-discovery-test";
    public static final String MARKER = "x-discovery-marker: rhcl-test";

    private RegistryDiscoveryMarkers() {
    }

    public static boolean isDiscoveryService(ConversionContext ctx) {
        return ctx.service != null && DISCOVERY_SYSTEM_NAME.equals(ctx.service.systemName);
    }
}
