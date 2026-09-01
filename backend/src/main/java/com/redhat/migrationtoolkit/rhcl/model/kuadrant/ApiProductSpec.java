package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "displayName",
        "description",
        "approvalMode",
        "publishStatus",
        "targetRef",
        "version"
})
public record ApiProductSpec(
        String displayName,
        String description,
        String approvalMode,
        String publishStatus,
        TargetRef targetRef,
        String version
) {
}
