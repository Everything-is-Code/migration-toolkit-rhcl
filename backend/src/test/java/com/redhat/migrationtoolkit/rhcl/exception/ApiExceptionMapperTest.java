package com.redhat.migrationtoolkit.rhcl.exception;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ApiExceptionMapperTest {

    @Test
    void mapApiException_validationException_returns400WithEnvelope() {
        given()
                .when().get("/test-exceptions/validation")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.message", equalTo("File upload is required"))
                .body("error.details", notNullValue());
    }

    @Test
    void mapApiException_validationExceptionWithDetails_returns400WithDetails() {
        given()
                .when().get("/test-exceptions/validation-details")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.details.field1", equalTo("must not be blank"));
    }

    @Test
    void mapApiException_threeScaleClientException_returns502WithEnvelope() {
        given()
                .when().get("/test-exceptions/threescale")
                .then()
                .statusCode(502)
                .body("error.code", equalTo("THREESCALE_CLIENT_ERROR"))
                .body("error.message", equalTo("3scale API unreachable"));
    }

    @Test
    void mapApiException_threeScaleClientExceptionWithCause_returns502() {
        given()
                .when().get("/test-exceptions/threescale-cause")
                .then()
                .statusCode(502)
                .body("error.code", equalTo("THREESCALE_CLIENT_ERROR"));
    }

    @Test
    void mapApiException_importParseException_returns400WithEnvelope() {
        given()
                .when().get("/test-exceptions/import-parse")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("IMPORT_NO_YAML"))
                .body("error.message", equalTo("No YAML files found in ZIP"));
    }

    @Test
    void mapApiException_importParseExceptionWithCause_returns400() {
        given()
                .when().get("/test-exceptions/import-parse-cause")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("IMPORT_PARSE_ERROR"))
                .body("error.message", equalTo("Failed to parse ZIP file"));
    }

    @Test
    void mapApiException_clusterApplyException_returns500WithEnvelope() {
        given()
                .when().get("/test-exceptions/cluster-apply")
                .then()
                .statusCode(500)
                .body("error.code", equalTo("APPLY_FAILED"));
    }

    @Test
    void mapGenericException_returns500WithInternalErrorEnvelope() {
        given()
                .when().get("/test-exceptions/generic-error")
                .then()
                .statusCode(500)
                .body("error.code", equalTo("INTERNAL_ERROR"))
                .body("error.message", equalTo("An internal error occurred"))
                .body("error.details", notNullValue());
    }

    @Test
    void mapConstraintViolation_returns400WithFieldDetails() {
        given()
                .queryParam("name", "")
                .when().get("/test-exceptions/constraint-violation")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.message", equalTo("Validation failed"));
    }

    @Test
    void mapApiException_notFoundException_returns404WithEnvelope() {
        given()
                .when().get("/test-exceptions/not-found")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("HISTORY_NOT_FOUND"))
                .body("error.message", equalTo("History entry 999 not found"))
                .body("error.details", notNullValue());
    }

    @Test
    void webApplicationException_passThrough_returns404() {
        given()
                .when().get("/test-exceptions/nonexistent-path")
                .then()
                .statusCode(404);
    }
}
