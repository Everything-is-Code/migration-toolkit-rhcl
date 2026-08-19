package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.dto.ServiceListPage;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import com.redhat.migrationtoolkit.rhcl.service.CompatibilityService;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
import java.util.Set;

@Path("/api/services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Services", description = "3scale service export")
public class ExportController {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    ThreeScaleExportService exportService;

    @Inject
    CompatibilityService compatibilityService;

    @Inject
    ClusterVersionService clusterVersionService;

    @GET
    @Operation(summary = "List one page of services (summary for selection UI)")
    public Response getServices(@QueryParam("url") String url,
                                 @QueryParam("page") @DefaultValue("1") int page,
                                 @QueryParam("perPage") @DefaultValue("20") int perPage,
                                 @HeaderParam("Authorization") String authorization) {
        String accessToken = extractBearerToken(authorization);
        if (url == null || url.isBlank() || accessToken == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("url query parameter and Authorization Bearer token are required")
                    .build();
        }
        ServiceListPage result = exportService.listServicesPage(url, accessToken, page, perPage);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a specific service from 3scale")
    public Response getService(@PathParam("id") String id,
                                @QueryParam("url") String url,
                                @HeaderParam("Authorization") String authorization) {
        String accessToken = extractBearerToken(authorization);
        if (url == null || url.isBlank() || accessToken == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("url query parameter and Authorization Bearer token are required")
                    .build();
        }
        ApiService service = exportService.exportService(url, accessToken, id);
        return Response.ok(service).build();
    }

    @GET
    @Path("/{id}/compatibility")
    @Operation(summary = "Check compatibility of a service")
    public Response checkCompatibility(@PathParam("id") String id,
                                        @QueryParam("url") String url,
                                        @HeaderParam("Authorization") String authorization,
                                        @QueryParam("supportedPolicies") String supportedPoliciesParam) {
        String accessToken = extractBearerToken(authorization);
        if (url == null || url.isBlank() || accessToken == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("url query parameter and Authorization Bearer token are required")
                    .build();
        }
        ApiService service = exportService.exportService(url, accessToken, id);
        Set<String> supportedPolicies = new HashSet<>();
        if (supportedPoliciesParam != null && !supportedPoliciesParam.isBlank()) {
            supportedPolicies.addAll(Arrays.asList(supportedPoliciesParam.split("\\|")));
        }
        CompatibilityResult result = compatibilityService.check(
                service, supportedPolicies, resolveCapabilities());
        return Response.ok(result).build();
    }

    private ClusterCapabilities resolveCapabilities() {
        ClusterVersionsResponse versions = clusterVersionService.resolveFromSettings(false);
        return versions != null ? versions.capabilities : null;
    }

    /**
     * Extracts the token from an {@code Authorization: Bearer <token>} header.
     * Query-string tokens are intentionally unsupported.
     *
     * @return token value, or {@code null} if missing/invalid
     */
    static String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()) {
            return null;
        }
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
