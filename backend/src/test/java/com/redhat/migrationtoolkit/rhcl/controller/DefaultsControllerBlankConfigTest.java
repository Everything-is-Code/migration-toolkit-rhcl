package com.redhat.migrationtoolkit.rhcl.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(DefaultsControllerBlankConfigTest.BlankProfile.class)
class DefaultsControllerBlankConfigTest {

    public static class BlankProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "threescale.default.url", "   ",
                    "threescale.default.token", "\t");
        }
    }

    @Test
    void getDefaults_blankValues_filteredAsUnconfigured() {
        given()
                .when().get("/api/defaults")
                .then()
                .statusCode(200)
                .body("threescale.configured", equalTo(false))
                .body("threescale.url", nullValue())
                .body("threescale.token", nullValue());
    }
}
