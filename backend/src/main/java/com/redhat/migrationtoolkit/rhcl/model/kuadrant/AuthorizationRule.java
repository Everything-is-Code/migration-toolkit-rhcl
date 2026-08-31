package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Named authorization rule body. Serializes as the inner map (e.g. patternMatching, opa).
 */
public record AuthorizationRule(@JsonValue Map<String, Object> value) {

    public AuthorizationRule {
        value = value == null ? Map.of() : value;
    }
}
