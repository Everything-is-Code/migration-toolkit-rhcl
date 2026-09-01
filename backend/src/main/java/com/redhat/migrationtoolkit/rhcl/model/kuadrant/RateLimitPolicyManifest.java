package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public record RateLimitPolicyManifest(
        String apiVersion,
        String kind,
        ManifestMeta metadata,
        RateLimitPolicySpec spec
) {
}
