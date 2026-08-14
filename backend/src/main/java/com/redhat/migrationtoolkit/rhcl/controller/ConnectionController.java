package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
    public Response testConnection(@Valid ConnectionRequest request) {
        boolean connected = exportService.testConnection(request);
        if (connected) {
            return Response.ok(Map.of("success", true, "message", "Successfully connected to 3scale")).build();
        } else {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("success", false,
                            "message", "Failed to connect to 3scale. Check URL and access token."))
                    .build();
        }
    }
}
