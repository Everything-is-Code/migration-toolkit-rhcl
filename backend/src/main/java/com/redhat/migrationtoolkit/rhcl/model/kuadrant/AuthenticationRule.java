package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Named authentication rule body. Serializes as the inner map (e.g. {@code jwt: { issuerUrl: ... }}).
 */
public record AuthenticationRule(@JsonValue Map<String, Object> value) {

    public AuthenticationRule {
        value = value == null ? Map.of() : value;
    }
}
