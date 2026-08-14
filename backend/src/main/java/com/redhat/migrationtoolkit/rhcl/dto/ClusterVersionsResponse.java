package com.redhat.migrationtoolkit.rhcl.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for {@code GET /api/cluster/versions}.
 */
public class ClusterVersionsResponse {
    public String ocp;
    public String gatewayApi;
    public String kuadrant;
    public String ossm;
    public String ossmExpectedForOcp;
    public ClusterCapabilities capabilities;
    /** {@code detected}, {@code profile}, or {@code default}. */
    public String source;
    /** {@code auto}, {@code ocp-4.19}, or {@code ocp-4.21}. */
    public String profile;
    /** Soft-fail notes; never contains tokens or kubeconfig paths. */
    public List<String> errors = new ArrayList<>();
}
