package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import com.redhat.migrationtoolkit.rhcl.service.CompatibilityService;
import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import com.redhat.migrationtoolkit.rhcl.service.ValidationService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ConversionControllerTest {

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void cleanUp() {
        em.createQuery("DELETE FROM ConversionHistoryEntity").executeUpdate();
        em.createQuery("DELETE FROM ProjectEntity").executeUpdate();
    }

    @InjectMock
    ThreeScaleExportService exportService;

    @InjectMock
    CompatibilityService compatibilityService;

    @InjectMock
    ConversionService conversionService;

    @InjectMock
    ValidationService validationService;

    @InjectMock
    ClusterVersionService clusterVersionService;

    // ── /api/convert ──────────────────────────────────────────────────────────

    @org.junit.jupiter.api.BeforeEach
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
    void convert_noServiceIds_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"serviceIds\":[], \"namespace\":\"test\", \"threescaleUrl\":\"https://x.com\", \"accessToken\":\"tok\"}")
                .when().post("/api/convert")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void convert_nullServiceIds_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"namespace\":\"test\", \"threescaleUrl\":\"https://x.com\", \"accessToken\":\"tok\"}")
                .when().post("/api/convert")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void convert_blankThreescaleUrl_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "test",
                          "threescaleUrl": "",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void convert_blankAccessToken_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "test",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "   "
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void convert_singleService_success() {
        ApiService svc = buildService("svc-1", "My API", "jwt");
        CompatibilityResult compat = buildCompat("svc-1", 90, "HIGH");

        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(compat);
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class))).thenReturn(
                Map.of("gateway.yaml", "kind: Gateway", "httproute.yaml", "kind: HTTPRoute"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "test-ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "my-token"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].serviceId", equalTo("svc-1"))
                .body("results[0].compatibilityScore", equalTo(90));
    }

    @Test
    void convert_multipleServices_allIncluded() {
        ApiService svc1 = buildService("svc-1", "API One", "jwt");
        ApiService svc2 = buildService("svc-2", "API Two", "apiKey");
        CompatibilityResult compat = buildCompat("svc-1", 80, "HIGH");

        when(exportService.exportService(anyString(), anyString(), eq("svc-1"))).thenReturn(svc1);
        when(exportService.exportService(anyString(), anyString(), eq("svc-2"))).thenReturn(svc2);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(compat);
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1", "svc-2"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(2));
    }

    @Test
    void convert_multipleServices_prefetchesExportsConcurrently() throws Exception {
        ApiService svc1 = buildService("svc-1", "API One", "jwt");
        ApiService svc2 = buildService("svc-2", "API Two", "apiKey");
        CountDownLatch bothExportsStarted = new CountDownLatch(2);

        when(exportService.exportService(anyString(), anyString(), eq("svc-1"))).thenAnswer(inv -> {
            bothExportsStarted.countDown();
            assertTrue(bothExportsStarted.await(3, TimeUnit.SECONDS),
                    "exports must start concurrently before either returns");
            return svc1;
        });
        when(exportService.exportService(anyString(), anyString(), eq("svc-2"))).thenAnswer(inv -> {
            bothExportsStarted.countDown();
            assertTrue(bothExportsStarted.await(3, TimeUnit.SECONDS),
                    "exports must start concurrently before either returns");
            return svc2;
        });
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 80, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1", "svc-2"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(2));

        verify(exportService, times(1)).exportService(anyString(), anyString(), eq("svc-1"));
        verify(exportService, times(1)).exportService(anyString(), anyString(), eq("svc-2"));
        InOrder convertOrder = inOrder(conversionService);
        convertOrder.verify(conversionService).convert(eq(svc1), anyString(), isNull(), any(ConversionOptions.class));
        convertOrder.verify(conversionService).convert(eq(svc2), anyString(), isNull(), any(ConversionOptions.class));
    }

    @Test
    void convert_multipleServices_convertRemainsSequentialAfterPrefetch() throws Exception {
        ApiService svc1 = buildService("svc-1", "API One", "jwt");
        ApiService svc2 = buildService("svc-2", "API Two", "apiKey");
        CountDownLatch firstConvertEntered = new CountDownLatch(1);
        CountDownLatch allowFirstConvertToFinish = new CountDownLatch(1);
        AtomicInteger concurrentConverts = new AtomicInteger(0);
        AtomicInteger maxConcurrentConverts = new AtomicInteger(0);

        when(exportService.exportService(anyString(), anyString(), eq("svc-1"))).thenReturn(svc1);
        when(exportService.exportService(anyString(), anyString(), eq("svc-2"))).thenReturn(svc2);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 80, "HIGH"));
        when(conversionService.convert(eq(svc1), anyString(), isNull(), any(ConversionOptions.class)))
                .thenAnswer(inv -> {
                    int n = concurrentConverts.incrementAndGet();
                    maxConcurrentConverts.updateAndGet(prev -> Math.max(prev, n));
                    firstConvertEntered.countDown();
                    assertTrue(allowFirstConvertToFinish.await(3, TimeUnit.SECONDS));
                    concurrentConverts.decrementAndGet();
                    return Map.of("gateway.yaml", "kind: Gateway");
                });
        when(conversionService.convert(eq(svc2), anyString(), isNull(), any(ConversionOptions.class)))
                .thenAnswer(inv -> {
                    int n = concurrentConverts.incrementAndGet();
                    maxConcurrentConverts.updateAndGet(prev -> Math.max(prev, n));
                    concurrentConverts.decrementAndGet();
                    return Map.of("gateway.yaml", "kind: Gateway");
                });

        Thread requester = new Thread(() -> given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1", "svc-2"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(2)));
        requester.start();
        assertTrue(firstConvertEntered.await(3, TimeUnit.SECONDS), "first convert should start");
        // While first convert is blocked, second must not have entered convert yet.
        assertEquals(1, concurrentConverts.get());
        allowFirstConvertToFinish.countDown();
        requester.join(5_000);
        assertFalse(requester.isAlive(), "request thread should finish within timeout");
        assertEquals(1, maxConcurrentConverts.get(), "convert+persist must stay sequential");
    }

    @Test
    void convert_oneExportFails_otherServiceStillSucceeds() {
        ApiService svc2 = buildService("svc-2", "API Two", "apiKey");
        when(exportService.exportService(anyString(), anyString(), eq("svc-1")))
                .thenThrow(new RuntimeException("export boom"));
        when(exportService.exportService(anyString(), anyString(), eq("svc-2"))).thenReturn(svc2);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-2", 80, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1", "svc-2"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("results.find { it.serviceId == 'svc-1' }.status", equalTo("FAILED"))
                .body("results.find { it.serviceId == 'svc-2' }.compatibilityScore", equalTo(80));

        verify(conversionService, times(1)).convert(eq(svc2), anyString(), isNull(), any(ConversionOptions.class));
    }

    @Test
    void convert_defaultNamespaceWhenNull() {
        ApiService svc = buildService("svc-1", "My API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(buildCompat("svc-1", 70, "MEDIUM"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);
    }

    @Test
    void convert_exportServiceThrows_serviceMarkedFailed() {
        when(exportService.exportService(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("3scale unavailable"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["bad-svc"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results[0].status", equalTo("FAILED"))
                .body("results[0].error", notNullValue());
    }

    @Test
    void convert_withExternalBackendUrl_passed() {
        ApiService svc = buildService("svc-1", "API One", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(buildCompat("svc-1", 80, "HIGH"));
        when(conversionService.convert(any(), anyString(), anyString(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok",
                          "externalBackendUrl": "https://api.external.example.com"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results", hasSize(1));
    }

    @Test
    void convert_systemNameNormalized_toKebabCase() {
        ApiService svc = buildService("svc-1", "My Great API", "jwt");
        svc.systemName = "My Great API";
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(buildCompat("svc-1", 85, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200)
                .body("results[0].packageName", equalTo("my-great-api"));
    }

    @Test
    void convert_ipCheckMode_passedToConversionOptions() {
        ApiService svc = buildService("svc-1", "IP API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok",
                          "ipCheckMode": "authPolicyOpa"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals("authPolicyOpa", optsCaptor.getValue().ipCheckMode);
    }

    @Test
    void convert_ipCheckMode_defaultsToAuthorizationPolicy() {
        ApiService svc = buildService("svc-1", "IP API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities.class))).thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals("authorizationPolicy", optsCaptor.getValue().ipCheckMode);
    }

    @Test
    void convert_threadsCorsNativeFalseFromClusterCapabilities() {
        ApiService svc = buildService("svc-1", "CORS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(false, optsCaptor.getValue().corsNative);
    }

    @Test
    void convert_tlsPolicyFields_passedToConversionOptions() {
        ApiService svc = buildService("svc-1", "TLS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok",
                          "includeTlsPolicy": true,
                          "tlsIssuerKind": "ClusterIssuer",
                          "tlsIssuerName": "letsencrypt-prod"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(true, optsCaptor.getValue().includeTlsPolicy);
        assertEquals("ClusterIssuer", optsCaptor.getValue().tlsIssuerKind);
        assertEquals("letsencrypt-prod", optsCaptor.getValue().tlsIssuerName);
    }

    @Test
    void convert_tlsPolicy_defaultsOff() {
        ApiService svc = buildService("svc-1", "TLS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(false, optsCaptor.getValue().includeTlsPolicy);
    }

    @Test
    void convert_dnsPolicyFields_passedToConversionOptions() {
        ApiService svc = buildService("svc-1", "DNS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok",
                          "includeDnsPolicy": true,
                          "dnsHostname": "my-app.apps.cluster.example.com",
                          "dnsProviderSecretName": "aws-credentials"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(true, optsCaptor.getValue().includeDnsPolicy);
        assertEquals("my-app.apps.cluster.example.com", optsCaptor.getValue().dnsHostname);
        assertEquals("aws-credentials", optsCaptor.getValue().dnsProviderSecretName);
    }

    @Test
    void convert_dnsPolicy_defaultsOff() {
        ApiService svc = buildService("svc-1", "DNS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(false, optsCaptor.getValue().includeDnsPolicy);
    }

    @Test
    void convert_dnsPolicyWithoutHostname_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok",
                          "includeDnsPolicy": true
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.message", containsString("dnsHostname"));

        verify(conversionService, never()).convert(any(), anyString(), isNull(), any(ConversionOptions.class));
    }

    @Test
    void convert_globalFailure_returns500WithEnvelope() {
        when(clusterVersionService.resolveFromSettings(anyBoolean()))
                .thenThrow(new RuntimeException("Infrastructure failure"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(500)
                .body("error.code", equalTo("INTERNAL_ERROR"))
                .body("error.message", equalTo("An internal error occurred"));
    }

    @Test
    void convert_threadsCorsNativeTrueFromClusterCapabilities() {
        ClusterVersionsResponse versions = new ClusterVersionsResponse();
        versions.capabilities = new ClusterCapabilities();
        versions.capabilities.corsNative = true;
        versions.capabilities.timeoutsSupported = true;
        versions.source = "default";
        versions.profile = "auto";
        when(clusterVersionService.resolveFromSettings(org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(versions);

        ApiService svc = buildService("svc-1", "CORS API", "jwt");
        when(exportService.exportService(anyString(), anyString(), anyString())).thenReturn(svc);
        when(compatibilityService.check(any(), any(), org.mockito.ArgumentMatchers.nullable(ClusterCapabilities.class)))
                .thenReturn(buildCompat("svc-1", 90, "HIGH"));
        when(conversionService.convert(any(), anyString(), isNull(), any(ConversionOptions.class)))
                .thenReturn(Map.of("gateway.yaml", "kind: Gateway"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "serviceIds": ["svc-1"],
                          "namespace": "ns",
                          "threescaleUrl": "https://3scale.example.com",
                          "accessToken": "tok"
                        }
                        """)
                .when().post("/api/convert")
                .then()
                .statusCode(200);

        ArgumentCaptor<ConversionOptions> optsCaptor = ArgumentCaptor.forClass(ConversionOptions.class);
        verify(conversionService).convert(any(), anyString(), isNull(), optsCaptor.capture());
        assertEquals(true, optsCaptor.getValue().corsNative);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ApiService buildService(String id, String name, String authType) {
        ApiService svc = new ApiService();
        svc.id = id;
        svc.name = name;
        svc.systemName = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        Authentication auth = new Authentication();
        auth.type = authType;
        svc.authentication = auth;
        return svc;
    }

    private CompatibilityResult buildCompat(String serviceId, int score, String level) {
        CompatibilityResult r = new CompatibilityResult();
        r.serviceId = serviceId;
        r.score = score;
        r.level = level;
        r.items = List.of();
        return r;
    }
}
