package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
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

    @InjectMock
    ClusterVersionService clusterVersionService;

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

    @Test
    void getVersions_returns200() {
        ClusterVersionsResponse versions = new ClusterVersionsResponse();
        versions.capabilities = new ClusterCapabilities();
        when(clusterVersionService.resolveFromSettings(false)).thenReturn(versions);

        given()
                .when().get("/api/cluster/versions")
                .then()
                .statusCode(200);
    }

    @Test
    void getVersions_refreshTrue_passesRefreshFlag() {
        ClusterVersionsResponse versions = new ClusterVersionsResponse();
        versions.capabilities = new ClusterCapabilities();
        when(clusterVersionService.resolveFromSettings(true)).thenReturn(versions);

        given()
                .queryParam("refresh", true)
                .when().get("/api/cluster/versions")
                .then()
                .statusCode(200);

        verify(clusterVersionService).resolveFromSettings(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_allowListProbeThrows_continuesToNextNamespace() {
        GenericKubernetesResource route = backendRoute("default",
                "migration-tool-backend-default.apps.cluster.example.com");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockToolkitNs = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockDefaultNs = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockToolkitRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        var mockDefaultRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(eq("migration-toolkit"))).thenReturn(mockToolkitNs);
        when(mockOp.inNamespace(eq("default"))).thenReturn(mockDefaultNs);
        when(mockToolkitNs.withName(eq(ClusterController.BACKEND_ROUTE_NAME))).thenReturn(mockToolkitRes);
        when(mockDefaultNs.withName(eq(ClusterController.BACKEND_ROUTE_NAME))).thenReturn(mockDefaultRes);
        when(mockToolkitRes.get()).thenThrow(new RuntimeException("probe failed"));
        when(mockDefaultRes.get()).thenReturn(route);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"))
                .body("namespace", equalTo("default"));

        assertEquals(0, clusterController.clusterWideListInvocations);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_hostFromStatusIngress_returns200() {
        GenericKubernetesResource route = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(ClusterController.BACKEND_ROUTE_NAME);
        meta.setNamespace("migration-toolkit");
        route.setMetadata(meta);
        Map<String, Object> additional = new HashMap<>();
        additional.put("status", Map.of("ingress", List.of(Map.of("host",
                "migration-tool-backend-migration-toolkit.apps.cluster.example.com"))));
        route.setAdditionalProperties(additional);

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
                .statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_cacheExpired_reprobesRoute() throws Exception {
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

        Field cacheAtField = ClusterController.class.getDeclaredField("domainCacheAt");
        cacheAtField.setAccessible(true);
        cacheAtField.setLong(null, System.currentTimeMillis() - ClusterController.DOMAIN_CACHE_TTL_MS - 1);

        given().when().get("/api/cluster/domain").then().statusCode(200);
        verify(mockRes, times(2)).get();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_clusterWideFallback_findsRoute() {
        GenericKubernetesResource route = backendRoute("other-ns",
                "migration-tool-backend-other-ns.apps.cluster.example.com");

        var mockOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var mockNsOp = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockAnyNs = Mockito.mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        var mockRes = Mockito.mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(kubernetesClient.genericKubernetesResources(any())).thenReturn(mockOp);
        when(mockOp.inNamespace(anyString())).thenReturn(mockNsOp);
        when(mockNsOp.withName(anyString())).thenReturn(mockRes);
        when(mockRes.get()).thenReturn(null);
        when(mockOp.inAnyNamespace()).thenReturn(mockAnyNs);
        GenericKubernetesResourceList list = new GenericKubernetesResourceList();
        list.setItems(List.of(route));
        when(mockAnyNs.list()).thenReturn(list);

        given()
                .when().get("/api/cluster/domain")
                .then()
                .statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"))
                .body("namespace", equalTo("other-ns"));

        verify(mockAnyNs).list();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDomain_routeWithoutNamespace_omitsNamespaceFromResponse() {
        GenericKubernetesResource route = backendRoute(null,
                "migration-tool-backend.apps.cluster.example.com");

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
                .statusCode(200)
                .body("domain", equalTo("apps.cluster.example.com"))
                .body("namespace", nullValue());
    }

    private static GenericKubernetesResource backendRoute(String ns, String host) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(ClusterController.BACKEND_ROUTE_NAME);
        if (ns != null) {
            meta.setNamespace(ns);
        }
        route.setMetadata(meta);
        Map<String, Object> additional = new HashMap<>();
        additional.put("spec", Map.of("host", host));
        route.setAdditionalProperties(additional);
        return route;
    }
}
