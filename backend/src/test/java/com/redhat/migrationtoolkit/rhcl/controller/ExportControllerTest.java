package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.dto.ServiceListPage;
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
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(clusterVersionService.resolveFromSettings(org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(versions);
    }

    @Test
    void getServices_missingParams_returns400() {
        given()
                .when().get("/api/services")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void getServices_queryOnlyAccessToken_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .queryParam("accessToken", "secret-from-query")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServicesPage(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void getServices_queryOnlySnakeCaseAccessToken_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .queryParam("access_token", "secret-from-query")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServicesPage(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void getServices_missingAuthorization_returns400() {
        given()
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServicesPage(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void getServices_invalidAuthorizationScheme_returns400() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(400);

        verify(exportService, never()).listServicesPage(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void getServices_withBearerAuthorization_returns200() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "Test API";
        ServiceListPage page = new ServiceListPage();
        page.items = List.of(svc);
        page.page = 1;
        page.perPage = 20;
        page.hasMore = false;
        page.total = 1;
        when(exportService.listServicesPage(eq("https://3scale.example.com"), eq("token123"), eq(1), eq(20)))
                .thenReturn(page);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo("1"))
                .body("page", equalTo(1))
                .body("perPage", equalTo(20))
                .body("hasMore", equalTo(false));

        verify(exportService).listServicesPage("https://3scale.example.com", "token123", 1, 20);
    }

    @Test
    void getServices_withPageParams_forwardsToService() {
        ServiceListPage page = new ServiceListPage();
        page.items = List.of();
        page.page = 2;
        page.perPage = 10;
        page.hasMore = true;
        when(exportService.listServicesPage(eq("https://3scale.example.com"), eq("token123"), eq(2), eq(10)))
                .thenReturn(page);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .queryParam("page", 2)
                .queryParam("perPage", 10)
                .when().get("/api/services")
                .then()
                .statusCode(200)
                .body("page", equalTo(2))
                .body("perPage", equalTo(10))
                .body("hasMore", equalTo(true));

        verify(exportService).listServicesPage("https://3scale.example.com", "token123", 2, 10);
    }

    @Test
    void getServices_ignoresQueryTokenWhenBearerPresent() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "Test API";
        ServiceListPage page = new ServiceListPage();
        page.items = List.of(svc);
        page.page = 1;
        page.perPage = 20;
        when(exportService.listServicesPage(eq("https://3scale.example.com"), eq("header-token"), eq(1), eq(20)))
                .thenReturn(page);

        given()
                .header("Authorization", "Bearer header-token")
                .queryParam("url", "https://3scale.example.com")
                .queryParam("accessToken", "query-token")
                .when().get("/api/services")
                .then()
                .statusCode(200);

        verify(exportService).listServicesPage("https://3scale.example.com", "header-token", 1, 20);
        verify(exportService, never()).listServicesPage(anyString(), eq("query-token"), anyInt(), anyInt());
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

    @Test
    void getServices_blankUrl_returns400() {
        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "   ")
                .when().get("/api/services")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void getServices_bearerTokenEmpty_returns400() {
        given()
                .header("Authorization", "Bearer   ")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services")
                .then()
                .statusCode(400);
    }

    @Test
    void checkCompatibility_withSupportedPolicies_passesToService() {
        ApiService svc = new ApiService();
        svc.id = "42";
        svc.name = "My API";
        Authentication auth = new Authentication();
        auth.type = "jwt";
        svc.authentication = auth;

        CompatibilityResult result = new CompatibilityResult();
        result.serviceId = "42";
        result.score = 75;
        result.level = "MEDIUM";
        result.items = List.of();

        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(result);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .queryParam("supportedPolicies", "cors|jwt")
                .when().get("/api/services/42/compatibility")
                .then()
                .statusCode(200)
                .body("score", equalTo(75));

        verify(compatibilityService).check(eq(svc), eq(Set.of("cors", "jwt")),
                org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class));
    }

    @Test
    void checkCompatibility_nullClusterVersions_stillReturns200() {
        when(clusterVersionService.resolveFromSettings(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(null);

        ApiService svc = new ApiService();
        svc.id = "42";
        svc.name = "My API";
        CompatibilityResult result = new CompatibilityResult();
        result.serviceId = "42";
        result.score = 60;
        result.level = "LOW";
        result.items = List.of();

        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), isNull())).thenReturn(result);

        given()
                .header("Authorization", "Bearer token123")
                .queryParam("url", "https://3scale.example.com")
                .when().get("/api/services/42/compatibility")
                .then()
                .statusCode(200)
                .body("score", equalTo(60));
    }
}
