package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.exception.ThreeScaleClientException;
import com.redhat.migrationtoolkit.rhcl.exception.ValidationException;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/connection")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Connection", description = "3scale connection management")
public class ConnectionController {

    @Inject
    ThreeScaleExportService exportService;

    @POST
    @Path("/test")
    @Operation(summary = "Test connection to 3scale")
    public Response testConnection(ConnectionRequest request) {
        if (request == null) {
            throw new ValidationException("url and accessToken are required");
        }
        if (request.url == null || request.url.isBlank()) {
            throw new ValidationException("url is required");
        }
        if (request.accessToken == null || request.accessToken.isBlank()) {
            throw new ValidationException("accessToken is required");
        }
        boolean connected = exportService.testConnection(request);
        if (connected) {
            return Response.ok(Map.of("success", true, "message", "Successfully connected to 3scale")).build();
        }
        throw new ThreeScaleClientException("CONNECTION_TEST_FAILED",
                "Failed to connect to 3scale. Check URL and access token.");
    }
}
