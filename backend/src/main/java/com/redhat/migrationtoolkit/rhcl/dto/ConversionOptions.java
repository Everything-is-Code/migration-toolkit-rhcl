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

    /**
     * When true, emit Gateway API HTTPRoute {@code type: CORS} filter.
     * When false (default — OCP 4.19 / GAPI 1.2.1), emit ResponseHeaderModifier
     * Access-Control-* headers plus OPTIONS matches.
     */
    public boolean corsNative = false;

    /**
     * When true, emit Kuadrant TLSPolicy targeting the generated Gateway.
     * Default OFF — packages unchanged until the user opts in.
     */
    public boolean includeTlsPolicy = false;

    /** cert-manager Issuer/ClusterIssuer kind for TLSPolicy issuerRef (e.g. ClusterIssuer). */
    public String tlsIssuerKind;

    /** cert-manager Issuer/ClusterIssuer name for TLSPolicy issuerRef (e.g. letsencrypt-prod). */
    public String tlsIssuerName;

    /**
     * When true, emit Kuadrant DNSPolicy and set Gateway listener hostnames.
     * Default OFF — packages unchanged until the user opts in.
     */
    public boolean includeDnsPolicy = false;

    /** Hostname applied to both Gateway http and https listeners when DNS is enabled. */
    public String dnsHostname;

    /**
     * Optional DNS provider Secret name for DNSPolicy providerRefs.
     * When blank, providerRefs is omitted (cluster default-provider Secret is used).
     */
    public String dnsProviderSecretName;
}
