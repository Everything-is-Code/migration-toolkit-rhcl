package com.redhat.migrationtoolkit.rhcl.exception;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ApiExceptionMapperResource {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapperResource.class);

    @ServerExceptionMapper
    public Response mapApiException(ApiException e) {
        LOG.warnf(e, "%s: %s", e.getCode(), e.getMessage());
        return Response.status(e.getStatus())
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(envelope(e.getCode(), ErrorSanitizer.sanitize(e.getMessage()), e.getDetails()))
            .build();
    }

    @ServerExceptionMapper
    public Response mapConstraintViolation(ConstraintViolationException e) {
        var details = e.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                v -> (Object) v.getMessage(),
                (a, b) -> a + "; " + b));
        return Response.status(400)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(envelope("VALIDATION_FAILED", "Validation failed", details))
            .build();
    }

    @ServerExceptionMapper
    public Response mapGenericException(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.errorf(e, "Unhandled exception: %s", e.getMessage());
        return Response.status(500)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(envelope("INTERNAL_ERROR", "An internal error occurred", Map.of()))
            .build();
    }

    private Map<String, Object> envelope(String code, String message, Map<String, Object> details) {
        return Map.of("error", Map.of("code", code, "message", message, "details", details));
    }
}
