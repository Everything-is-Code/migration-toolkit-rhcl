package com.redhat.migrationtoolkit.rhcl.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(DefaultsControllerConfiguredTest.ConfiguredProfile.class)
class DefaultsControllerConfiguredTest {

    public static class ConfiguredProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "threescale.default.url", "https://3scale.example.com",
                    "threescale.default.token", "secret-token");
        }
    }

    @Test
    void getDefaults_configured_returns200WithValues() {
        given()
                .when().get("/api/defaults")
                .then()
                .statusCode(200)
                .body("threescale.configured", equalTo(true))
                .body("threescale.url", equalTo("https://3scale.example.com"))
                .body("threescale.token", equalTo("secret-token"));
    }
}
