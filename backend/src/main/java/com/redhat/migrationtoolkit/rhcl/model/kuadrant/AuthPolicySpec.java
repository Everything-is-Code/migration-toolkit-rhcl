package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"targetRef", "rules"})
public record AuthPolicySpec(TargetRef targetRef, AuthPolicyRules rules) {
}
