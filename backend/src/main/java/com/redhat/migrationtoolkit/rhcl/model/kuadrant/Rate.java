package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"limit", "window"})
public record Rate(int limit, String window) {
}
