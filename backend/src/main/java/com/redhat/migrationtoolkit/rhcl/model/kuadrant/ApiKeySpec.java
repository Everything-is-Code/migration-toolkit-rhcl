package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"apiProductRef", "planTier", "requestedBy", "secretRef"})
public record ApiKeySpec(
        ApiProductRef apiProductRef,
        String planTier,
        RequestedBy requestedBy,
        SecretRef secretRef
) {
}
