package com.redhat.migrationtoolkit.rhcl.dto;

import jakarta.validation.constraints.NotBlank;

public class ConnectionRequest {
    @NotBlank
    public String url;
    @NotBlank
    public String accessToken;
    public String tenant;
}
