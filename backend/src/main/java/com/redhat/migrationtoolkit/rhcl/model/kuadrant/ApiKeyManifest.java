package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public record ApiKeyManifest(
        String apiVersion,
        String kind,
        ManifestMeta metadata,
        ApiKeySpec spec
) {
}
