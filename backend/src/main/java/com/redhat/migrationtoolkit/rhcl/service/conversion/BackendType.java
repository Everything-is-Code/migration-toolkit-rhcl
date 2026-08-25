package com.redhat.migrationtoolkit.rhcl.service.conversion;

/**
 * Resolved backend target for HTTPRoute backendRefs and optional Istio external resources.
 */
public enum BackendType {
    /** In-cluster service — no ServiceEntry / DestinationRule required. */
    INTERNAL,
    /** External HTTPS endpoint — ServiceEntry, DestinationRule, URL rewrite may apply. */
    EXTERNAL
}
