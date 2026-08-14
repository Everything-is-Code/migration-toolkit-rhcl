package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import com.redhat.migrationtoolkit.rhcl.service.CompatibilityService;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExportControllerTest {

    @InjectMock
    ThreeScaleExportService exportService;

    @InjectMock
    CompatibilityService compatibilityService;

    @InjectMock
    ClusterVersionService clusterVersionService;

    @BeforeEach
    void stubClusterVersions() {
        ClusterVersionsResponse versions = new ClusterVersionsResponse();
        versions.capabilities = new ClusterCapabilities();
        versions.capabilities.corsNative = false;
        versions.capabilities.timeoutsSupported = true;
        versions.source = "default";
        versions.profile = "auto";
        when(clusterVersionService.resolve(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(versions);
    }

    @Test
    void getServices_missingParams_returns400() {
        given()
                .when().get("/api/services")
                .then()
                .statusCode(400);
    }

    @Test
    void getServices_queryOnlyAccessToken_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .queryParam("accessToken", "secret-from-query")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServices(anyString(), anyString());
    }

    @Test
    void getServices_queryOnlySnakeCaseAccessToken_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .queryParam("access_token", "secret-from-query")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServices(anyString(), anyString());
    }

    @Test
    void getServices_missingAuthorization_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServices(anyString(), anyString());
    }

    @Test
    void getServices_invalidAuthorizationScheme_returns400() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServices(anyString(), anyString());
    }

    @Test
    void getServices_withBearerAuthorization_returns200() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "Test API";
        when(exportService.listServices(eq("https://3scale.example.com"), eq("token123")))
                .thenReturn(List.of(svc));

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo("1"));

        verify(exportService).listServices("https://3scale.example.com", "token123");
    }

    @Test
    void getServices_ignoresQueryTokenWhenBearerPresent() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "Test API";
        when(exportService.listServices(eq("https://3scale.example.com"), eq("header-token")))
                .thenReturn(List.of(svc));

        given()
                .header("Authorization", "Bearer header-token")
                .queryParam("url", "https://3scale.example.com")
                .queryParam("accessToken", "query-token")
                .when().get("/api/services")
                .then()
                .statusCode(200);

        verify(exportService).listServices("https://3scale.example.com", "header-token");
        verify(exportService, never()).listServices(anyString(), eq("query-token"));
    }

    @Test
    void getService_byId_withBearer_returns200() {
        ApiService svc = new ApiService();
        svc.id = "42";
        svc.name = "My API";
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services/42")
                .then()
                .statusCode(200)
                .body("id", equalTo("42"));
    }

    @Test
    void checkCompatibility_withBearer_returns200() {
        ApiService svc = new ApiService();
        svc.id = "42";
        svc.name = "My API";
        Authentication auth = new Authentication();
        auth.type = "jwt";
        svc.authentication = auth;

        CompatibilityResult result = new CompatibilityResult();
        result.serviceId = "42";
        result.score = 80;
        result.level = "HIGH";
        result.items = List.of();

        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(result);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services/42/compatibility")
                .then()
                .statusCode(200)
                .body("score", equalTo(80))
                .body("level", equalTo("HIGH"));
    }

    @Test
    void cors_preflight_localhost5173_allowed() {
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/services")
                .then()
                .statusCode(anyOf(is(200), is(204)))
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"));
    }

    @Test
    void cors_preflight_evilOrigin_notAllowed() {
        given()
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/services")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
