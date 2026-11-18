package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "displayName",
        "description",
        "approvalMode",
        "publishStatus",
        "targetRef",
        "version",
        "documentation"
})
public record ApiProductSpec(
        String displayName,
        String description,
        String approvalMode,
        String publishStatus,
        TargetRef targetRef,
        String version,
        @JsonInclude(JsonInclude.Include.NON_NULL) ApiProductDocumentation documentation
) {
    public ApiProductSpec(String displayName, String description, String approvalMode,
            String publishStatus, TargetRef targetRef, String version) {
        this(displayName, description, approvalMode, publishStatus, targetRef, version, null);
    }
}
