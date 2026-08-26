package com.redhat.migrationtoolkit.rhcl.exception;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Test-only REST resource for verifying exception mapper behaviour.
 * Not part of production code — only active during @QuarkusTest runs.
 */
@Path("/test-exceptions")
@Produces(MediaType.APPLICATION_JSON)
public class ExceptionTestResource {

    @GET
    @Path("/validation")
    public String throwValidation() {
        throw new ValidationException("File upload is required");
    }

    @GET
    @Path("/validation-details")
    public String throwValidationWithDetails() {
        throw new ValidationException("Multiple fields invalid",
                Map.of("field1", "must not be blank", "field2", "must be positive"));
    }

    @GET
    @Path("/threescale")
    public String throwThreeScale() {
        throw new ThreeScaleClientException("3scale API unreachable");
    }

    @GET
    @Path("/threescale-cause")
    public String throwThreeScaleWithCause() {
        throw new ThreeScaleClientException("Conversion failed: upstream error",
                new RuntimeException("upstream error"));
    }

    @GET
    @Path("/import-parse")
    public String throwImportParse() {
        throw ImportParseException.noYaml();
    }

    @GET
    @Path("/import-parse-cause")
    public String throwImportParseWithCause() {
        throw new ImportParseException("Failed to parse ZIP file",
                new java.io.IOException("corrupt zip"));
    }

    @GET
    @Path("/cluster-apply")
    public String throwClusterApply() {
        throw new ClusterApplyException("Apply operation failed",
                new RuntimeException("kube error"));
    }

    public record ConstraintBean(@NotBlank String name) {}

    @GET
    @Path("/constraint-violation")
    public String throwConstraintViolation(@Valid @QueryParam("name") @NotBlank String name) {
        return "ok";
    }

    @GET
    @Path("/not-found")
    public String throwNotFound() {
        throw new NotFoundException("HISTORY_NOT_FOUND", "History entry 999 not found");
    }

    @GET
    @Path("/generic-error")
    public String throwGenericError() {
        throw new RuntimeException("Something unexpected happened");
    }
}
