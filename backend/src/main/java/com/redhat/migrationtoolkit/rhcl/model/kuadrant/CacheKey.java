package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"selector"})
public record CacheKey(String selector) {
}
