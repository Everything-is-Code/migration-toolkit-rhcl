package com.redhat.migrationtoolkit.rhcl.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Path("/api/defaults")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Defaults", description = "Pre-configured defaults from environment variables")
public class DefaultsController {

    @ConfigProperty(name = "threescale.default.url")
    Optional<String> threescaleUrl;

    @ConfigProperty(name = "threescale.default.token")
    Optional<String> threescaleToken;

    @GET
    @Operation(summary = "Retrieve pre-configured defaults for 3scale connection")
    public Response getDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();

        Map<String, Object> threescale = new LinkedHashMap<>();
        threescale.put("url", threescaleUrl.filter(s -> !s.isBlank()).orElse(null));
        threescale.put("token", threescaleToken.filter(s -> !s.isBlank()).orElse(null));
        threescale.put("configured", threescaleUrl.filter(s -> !s.isBlank()).isPresent());

        defaults.put("threescale", threescale);
        return Response.ok(defaults).build();
    }
}
