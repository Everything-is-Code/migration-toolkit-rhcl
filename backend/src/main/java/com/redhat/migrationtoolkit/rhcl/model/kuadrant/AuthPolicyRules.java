package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"authentication", "authorization", "response"})
public record AuthPolicyRules(
        Map<String, AuthenticationRule> authentication,
        Map<String, AuthorizationRule> authorization,
        ResponseConfig response
) {

    public AuthPolicyRules {
        authentication = authentication == null ? Map.of() : authentication;
    }
}
