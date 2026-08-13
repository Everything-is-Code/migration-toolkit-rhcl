package com.redhat.migrationtoolkit.rhcl.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Runtime coverage for CORS_ORIGINS / quarkus.http.cors.origins override (Slice A).
 * Default allowlist tests live in {@link ExportControllerTest}.
 */
@QuarkusTest
@TestProfile(ExportCorsOriginsOverrideTest.CorsOriginsOverrideProfile.class)
class ExportCorsOriginsOverrideTest {

    public static class CorsOriginsOverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Simulates CORS_ORIGINS env / OCP demo override of the localhost default allowlist.
            return Map.of("quarkus.http.cors.origins", "https://demo.example.com");
        }
    }

    @Test
    void options_overriddenOrigin_allowsConfiguredDemoOrigin() {
        given()
                .header("Origin", "https://demo.example.com")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/services")
                .then()
                .statusCode(anyOf(200, 204))
                .header("Access-Control-Allow-Origin", equalTo("https://demo.example.com"));
    }

    @Test
    void options_localhostOrigin_deniedWhenOverrideReplacesAllowlist() {
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/services")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    private static org.hamcrest.Matcher<Integer> anyOf(int a, int b) {
        return org.hamcrest.Matchers.anyOf(equalTo(a), equalTo(b));
    }
}
