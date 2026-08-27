package com.redhat.migrationtoolkit.rhcl.dto;

/**
 * Capability flags derived from resolved OCP / Gateway API / Kuadrant / OSSM versions.
 */
public class ClusterCapabilities {
    /**
     * True when the backend successfully probed the OpenShift cluster ({@code source: detected}
     * or manual profile). False on soft-fail defaults after auth/timeout/missing client.
     */
    public boolean clusterReachable;
    /** True when Gateway API CORS filter (type: CORS) is available (GAPI ≥ 1.3 or OCP ≥ 4.21). */
    public boolean corsNative;
    /** True when a Kuadrant/RHCL operator CSV was found (or profile assumes present). */
    public boolean kuadrantPresent;
    /** True when an OSSM operator CSV and/or SMCP was found (or mapped expected is used as present for profile). */
    public boolean ossmPresent;
    /** True when detected OSSM aligns with the OCP↔OSSM expected matrix for the resolved OCP. */
    public boolean ossmMatchesOcp;
    /** True when Gateway API timeouts are supported (GAPI ≥ 1.1 / OCP ≥ 4.18). */
    public boolean timeoutsSupported;
    /**
     * True when Gateway API HTTPRoute {@code retry.attempts} is available
     * (GAPI ≥ 1.2 / OCP ≥ 4.19). When false, ConversionService emits EnvoyFilter fallback.
     */
    public boolean retriesSupported;
}
