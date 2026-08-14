package com.redhat.migrationtoolkit.rhcl.dto;

/**
 * Convert-time preferences for YAML generation.
 * Defaults match the previous positional overloads and binding decisions.
 */
public class ConversionOptions {
    /** Logging Telemetry target: "gateway" (default) or "workload". */
    public String loggingTarget = "gateway";

    /** Anonymous Access AuthPolicy targetRef: "httproute" (default) or "gateway". */
    public String anonymousTarget = "httproute";

    /** Whether to include the "migrated-from: 3scale" label (default: true). */
    public boolean includeMigratedFromLabel = true;

    /**
     * ip_check emit target: "authorizationPolicy" (default) or "authPolicyOpa".
     * Only the selected target is emitted.
     */
    public String ipCheckMode = "authorizationPolicy";
}
