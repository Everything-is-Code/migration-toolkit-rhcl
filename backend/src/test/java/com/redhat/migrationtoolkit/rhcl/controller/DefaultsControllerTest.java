package com.redhat.migrationtoolkit.rhcl.controller;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class DefaultsControllerTest {

    @Test
    void getDefaults_unconfigured_returns200WithNullFields() {
        given()
                .when().get("/api/defaults")
                .then()
                .statusCode(200)
                .body("threescale.configured", equalTo(false))
                .body("threescale.url", nullValue())
                .body("threescale.token", nullValue());
    }
}
