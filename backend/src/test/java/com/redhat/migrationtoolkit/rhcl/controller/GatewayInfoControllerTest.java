package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.util.GatewayDnsResolver;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class GatewayInfoControllerTest {

    @InjectMock
    @MockitoConfig(convertScopes = true)
    KubernetesClient kubernetesClient;

    @InjectMock
    @MockitoConfig(convertScopes = true)
    GatewayDnsResolver dnsResolver;

    @Test
    void getGatewayInfo_missingNamespace_returns400() {
        given()
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.message", notNullValue());
    }

    @Test
    void getGatewayInfo_missingName_returns400() {
        given()
                .queryParam("namespace", "test-ns")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"))
                .body("error.message", notNullValue());
    }

    @Test
    void getGatewayInfo_bothMissing_returns400() {
        given()
                .when().get("/api/gateway/info")
                .then()
                .statusCode(400);
    }

    @Test
    void getGatewayInfo_clientError_returns500WithEnvelope() {
        when(kubernetesClient.genericKubernetesResources(any()))
                .thenThrow(new RuntimeException("Connection refused"));

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(500)
                .body("error.code", equalTo("INTERNAL_ERROR"))
                .body("error.message", notNullValue());
    }

    @Test
    void getGatewayInfo_gatewayNotFound_returns404() {
        var mockResources = Mockito.mock(
                io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNamespaced = Mockito.mock(
                io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockResource = Mockito.mock(
                io.fabric8.kubernetes.client.dsl.Resource.class);

        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockResources);
        when(mockResources.inNamespace(any())).thenReturn(mockNamespaced);
        when(mockNamespaced.withName(any())).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(null);

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("GATEWAY_NOT_FOUND"))
                .body("error.message", notNullValue());
    }

    @Test
    void getGatewayInfo_gatewayFound_withHostname_returns200() {
        GenericKubernetesResource gw = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("my-gateway");
        gw.setMetadata(meta);
        Map<String, Object> props = new HashMap<>();
        props.put("status", Map.of(
                "addresses", List.of(Map.of("type", "Hostname", "value", "lb.example.com"))
        ));
        gw.setAdditionalProperties(props);

        var mockResources = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNamespaced = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockResource = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);

        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockResources);
        when(mockResources.inNamespace(anyString())).thenReturn(mockNamespaced);
        when(mockNamespaced.withName(anyString())).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(gw);

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(200)
                .body("hostname", equalTo("lb.example.com"))
                .body("ready", is(true))
                .body("httpUrl", containsString("lb.example.com"));
    }

    @Test
    void getGatewayInfo_gatewayFound_noAddress_returns200() {
        GenericKubernetesResource gw = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("my-gateway");
        gw.setMetadata(meta);
        gw.setAdditionalProperties(new HashMap<>());

        var mockResources = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNamespaced = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockResource = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);

        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockResources);
        when(mockResources.inNamespace(anyString())).thenReturn(mockNamespaced);
        when(mockNamespaced.withName(anyString())).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(gw);

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(200)
                .body("ready", is(false))
                .body("hostname", equalTo(""));
    }

    @Test
    void getGatewayInfo_resolvableIpAddress_dnsReadyTrue() {
        GenericKubernetesResource gw = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("my-gateway");
        gw.setMetadata(meta);
        Map<String, Object> props = new HashMap<>();
        props.put("status", Map.of(
                "addresses", List.of(Map.of("type", "IPAddress", "value", "127.0.0.1"))
        ));
        gw.setAdditionalProperties(props);

        var mockResources = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNamespaced = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockResource = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);

        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockResources);
        when(mockResources.inNamespace(anyString())).thenReturn(mockNamespaced);
        when(mockNamespaced.withName(anyString())).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(gw);
        when(dnsResolver.isResolvable("127.0.0.1")).thenReturn(true);

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(200)
                .body("hostname", equalTo("127.0.0.1"))
                .body("ready", is(true))
                .body("dnsReady", is(true));
    }

    @Test
    void getGatewayInfo_unresolvableHostname_dnsReadyFalse() {
        GenericKubernetesResource gw = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("my-gateway");
        gw.setMetadata(meta);
        Map<String, Object> props = new HashMap<>();
        props.put("status", Map.of(
                "addresses", List.of(Map.of("type", "Hostname", "value", "definitely-invalid-hostname-xyz.invalid"))
        ));
        gw.setAdditionalProperties(props);

        var mockResources = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNamespaced = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockResource = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);

        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockResources);
        when(mockResources.inNamespace(anyString())).thenReturn(mockNamespaced);
        when(mockNamespaced.withName(anyString())).thenReturn(mockResource);
        when(mockResource.get()).thenReturn(gw);
        when(dnsResolver.isResolvable("definitely-invalid-hostname-xyz.invalid")).thenReturn(false);

        given()
                .queryParam("namespace", "test-ns")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(200)
                .body("ready", is(true))
                .body("dnsReady", is(false));
    }

    @Test
    void getGatewayInfo_blankNamespace_returns400() {
        given()
                .queryParam("namespace", "   ")
                .queryParam("name", "my-gateway")
                .when().get("/api/gateway/info")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }
}
