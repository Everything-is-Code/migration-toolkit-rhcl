package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public record ApiProductManifest(
        String apiVersion,
        String kind,
        ManifestMeta metadata,
        ApiProductSpec spec
) {
}
