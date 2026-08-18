package com.redhat.migrationtoolkit.rhcl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ConversionRequest {
    @NotBlank
    public String threescaleUrl;
    @NotBlank
    public String accessToken;
    public String tenant;
    public String namespace;
    @NotEmpty
    public List<String> serviceIds;
    /** External backend URL (e.g. https://foo.ecs.us-east-2.on.aws/api).
     * When specified, generates ServiceEntry + DestinationRule + Host rewrite. */
    public String externalBackendUrl;
    /** List of policy display names considered "supported" in the compatibility check. */
    public List<String> supportedPolicies;
    /** Target for the Logging policy: "gateway" (default) or "workload" */
    public String loggingTarget;
    /** targetRef for the Anonymous Access policy: "httproute" (default) or "gateway" */
    public String anonymousTarget;
    /** Whether to add the "migrated-from: 3scale" label to generated resources (default: true). */
    public Boolean includeMigratedFromLabel;
    /**
     * ip_check emit target: "authorizationPolicy" (default) or "authPolicyOpa".
     * Convert-time preference — same pattern as anonymousTarget.
     */
    public String ipCheckMode;

    /** When true, emit Kuadrant TLSPolicy (default: false). */
    public Boolean includeTlsPolicy;

    /** cert-manager Issuer kind for TLSPolicy issuerRef. */
    public String tlsIssuerKind;

    /** cert-manager Issuer name for TLSPolicy issuerRef. */
    public String tlsIssuerName;
}
