package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Named rate-limit bucket under {@code spec.limits}. Connection-limit policies from 3scale
 * are modeled as a {@link Rate} with {@code window: 1s} (no separate counter type).
 */
@JsonPropertyOrder({"rates"})
public record LimitDefinition(List<Rate> rates) {
}
