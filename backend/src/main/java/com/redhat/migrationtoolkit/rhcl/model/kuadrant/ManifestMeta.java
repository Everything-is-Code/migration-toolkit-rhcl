package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "namespace", "labels", "annotations"})
public record ManifestMeta(
        String name,
        String namespace,
        Map<String, String> labels,
        Map<String, String> annotations
) {
}
