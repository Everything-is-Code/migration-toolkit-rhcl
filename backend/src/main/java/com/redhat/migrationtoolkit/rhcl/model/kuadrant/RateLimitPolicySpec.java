package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonPropertyOrder({"targetRef", "limits"})
public record RateLimitPolicySpec(TargetRef targetRef, Map<String, LimitDefinition> limits) {
}
