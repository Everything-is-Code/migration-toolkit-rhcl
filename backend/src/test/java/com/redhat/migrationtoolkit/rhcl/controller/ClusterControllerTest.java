package com.redhat.migrationtoolkit.rhcl.controller;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ClusterControllerTest {

    @InjectMock
    @MockitoConfig(convertScopes = true)
    KubernetesClient kubernetesClient;

    @Inject
    ClusterController clusterController;

    @BeforeEach
    void resetCache() {
        clusterController.clearDomainCache();
        clusterController.clusterWideListInvocations = 0;
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_allowListHit_avoidsClusterWideList() {
        GenericKubernetesResource route = backendRoute("migration-toolkit",
                "migration-tool-backend-migration-toolkit.apps.cluster.example.com");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(eq("migration-toolkit"))).thenReturn(mockNsOp);
        when(mockOp.inNamespace(eq("default"))).thenReturn(mockNsOp);
        when(mockNsOp.withName(eq(ClusterController.BACKEND_ROUTE_NAME))).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(route);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"))
                .body("namespace", equalTo("migration-toolkit"));

        assertEquals(0, clusterController.clusterWideListInvocations);
        verify(mockOp, never()).inAnyNamespace();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_cacheHit_skipsSecondProbe() {
        GenericKubernetesResource route = backendRoute("migration-toolkit",
                "migration-tool-backend-migration-toolkit.apps.cluster.example.com");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(anyString())).thenReturn(mockNsOp);
        when(mockNsOp.withName(anyString())).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(route);

        given().when().get("/api/cluster/domain").then().statusCode(200);
        given().when().get("/api/cluster/domain").then().statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"));

        // Second call served from TTL cache — allow-list get only once on cold miss path
        verify(mockRes, times(1)).get();
        assertEquals(0, clusterController.clusterWideListInvocations);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_routeNotFound_returns404Envelope() {
        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        var mockAnyNs = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        GenericKubernetesResourceList emptyList = new GenericKubernetesResourceList();
        emptyList.setItems(java.util.List.of());
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(anyString())).thenReturn(mockNsOp);
        when(mockNsOp.withName(anyString())).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(null);
        when(mockOp.inAnyNamespace()).thenReturn(mockAnyNs);
        when(mockAnyNs.list()).thenReturn(emptyList);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("CLUSTER_ROUTE_NOT_FOUND"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_routeHostPending_returns404Envelope() {
        GenericKubernetesResource route = backendRoute("migration-toolkit", "");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(eq("migration-toolkit"))).thenReturn(mockNsOp);
        when(mockNsOp.withName(eq(ClusterController.BACKEND_ROUTE_NAME))).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(route);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("CLUSTER_ROUTE_HOST_PENDING"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_domainExtractFailed_returns404Envelope() {
        GenericKubernetesResource route = backendRoute("migration-toolkit",
                "migration-tool-backend-migration-toolkit.example.com");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(eq("migration-toolkit"))).thenReturn(mockNsOp);
        when(mockNsOp.withName(eq(ClusterController.BACKEND_ROUTE_NAME))).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(route);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("CLUSTER_DOMAIN_EXTRACT_FAILED"));
    }

    private static GenericKubernetesResource backendRoute(String ns, String host) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(ClusterController.BACKEND_ROUTE_NAME);
        meta.setNamespace(ns);
        route.setMetadata(meta);
        Map<String, Object> additional = new HashMap<>();
        additional.put("spec", Map.of("host", host));
        route.setAdditionalProperties(additional);
        return route;
    }
}
