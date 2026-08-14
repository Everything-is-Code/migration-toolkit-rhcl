package com.redhat.migrationtoolkit.rhcl.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level Bean Validation contracts for key request DTOs.
 * Expects {@code quarkus-hibernate-validator} (or a BV provider) on the classpath.
 */
class BeanValidationDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void connectionRequest_blankUrl_isInvalid() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = "";
        req.accessToken = "token123";

        Set<ConstraintViolation<ConnectionRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty(), "blank url must violate @NotBlank");
        assertTrue(violations.stream().anyMatch(v -> "url".equals(v.getPropertyPath().toString())));
    }

    @Test
    void connectionRequest_blankAccessToken_isInvalid() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = "https://3scale.example.com";
        req.accessToken = "   ";

        Set<ConstraintViolation<ConnectionRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty(), "blank accessToken must violate @NotBlank");
        assertTrue(violations.stream().anyMatch(v -> "accessToken".equals(v.getPropertyPath().toString())));
    }

    @Test
    void connectionRequest_validFields_hasNoViolations() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = "https://3scale.example.com";
        req.accessToken = "token123";

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void conversionRequest_blankThreescaleUrl_isInvalid() {
        ConversionRequest req = new ConversionRequest();
        req.threescaleUrl = "";
        req.accessToken = "tok";
        req.serviceIds = List.of("svc-1");

        Set<ConstraintViolation<ConversionRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "threescaleUrl".equals(v.getPropertyPath().toString())));
    }

    @Test
    void conversionRequest_emptyServiceIds_isInvalid() {
        ConversionRequest req = new ConversionRequest();
        req.threescaleUrl = "https://3scale.example.com";
        req.accessToken = "tok";
        req.serviceIds = List.of();

        Set<ConstraintViolation<ConversionRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "serviceIds".equals(v.getPropertyPath().toString())));
    }

    @Test
    void conversionRequest_validFields_hasNoViolations() {
        ConversionRequest req = new ConversionRequest();
        req.threescaleUrl = "https://3scale.example.com";
        req.accessToken = "tok";
        req.serviceIds = List.of("svc-1");

        assertTrue(validator.validate(req).isEmpty());
    }
}
