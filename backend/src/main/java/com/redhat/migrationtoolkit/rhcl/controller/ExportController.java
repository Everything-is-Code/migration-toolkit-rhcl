package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.service.CompatibilityService;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/api/services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Services", description = "3scale service export")
public class ExportController {

    @Inject
    ThreeScaleExportService exportService;

    @Inject
    CompatibilityService compatibilityService;

    @GET
    @Operation(summary = "Get all services from 3scale")
    public Response getServices(@QueryParam("url") String url,
                                 @QueryParam("accessToken") String accessToken) {
        if (url == null || accessToken == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("url and accessToken query parameters are required")
                    .build();
        }
        List<ApiService> services = exportService.exportServices(url, accessToken);
        return Response.ok(services).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a specific service from 3scale")
    public Response getService(@PathParam("id") String id,
                                @QueryParam("url") String url,
                                @QueryParam("accessToken") String accessToken) {
        ApiService service = exportService.exportService(url, accessToken, id);
        return Response.ok(service).build();
    }

    @GET
    @Path("/{id}/compatibility")
    @Operation(summary = "Check compatibility of a service")
    public Response checkCompatibility(@PathParam("id") String id,
                                        @QueryParam("url") String url,
                                        @QueryParam("accessToken") String accessToken,
                                        @QueryParam("supportedPolicies") String supportedPoliciesParam) {
        ApiService service = exportService.exportService(url, accessToken, id);
        Set<String> supportedPolicies = new HashSet<>();
        if (supportedPoliciesParam != null && !supportedPoliciesParam.isBlank()) {
            supportedPolicies.addAll(Arrays.asList(supportedPoliciesParam.split("\\|")));
        }
        CompatibilityResult result = compatibilityService.check(service, supportedPolicies);
        return Response.ok(result).build();
    }
}
