package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JaCoCo records {@link ClusterVersionService#capabilitiesFrom} reachability wiring
 * only through Quarkus-instrumented classes. Plain JUnit resolve tests assert behavior
 * but do not satisfy patch coverage on the new clusterReachable parameter paths.
 */
@QuarkusTest
class ClusterVersionServiceQuarkusTest {

  @Inject
  ClusterVersionService service;

  @InjectMock
  @MockitoConfig(convertScopes = true)
  KubernetesClient kubernetesClient;

  @BeforeEach
  void useSameThreadDetectExecutor() {
    service.useDetectExecutor(Runnable::run);
  }

  @Test
  void resolve_profile419_setsClusterReachable() {
    ClusterVersionsResponse response = service.resolve(ClusterVersionService.PROFILE_OCP_419, true);

    assertEquals("profile", response.source);
    assertTrue(response.capabilities.clusterReachable);
  }

  @Test
  void resolve_profile421_setsClusterReachable() {
    ClusterVersionsResponse response = service.resolve(ClusterVersionService.PROFILE_OCP_421, true);

    assertEquals("profile", response.source);
    assertTrue(response.capabilities.clusterReachable);
  }

  @Test
  void resolve_autoSoftFail_setsClusterUnreachable() {
    when(kubernetesClient.genericKubernetesResources(any(ResourceDefinitionContext.class)))
        .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

    ClusterVersionsResponse response = service.resolve(ClusterVersionService.PROFILE_AUTO, true);

    assertEquals("default", response.source);
    assertFalse(response.capabilities.clusterReachable);
  }

  @Test
  void resolve_detected_setsClusterReachable() {
    stubMinimalDetectedCluster("4.19.10", "1.2.1");

    ClusterVersionsResponse response = service.resolve(ClusterVersionService.PROFILE_AUTO, true);

    assertEquals("detected", response.source);
    assertTrue(response.capabilities.clusterReachable);
  }

  @Test
  void capabilitiesFrom_sixArg_setsReachableFlag() {
    assertTrue(ClusterVersionService.capabilitiesFrom(
        "4.21.0", "1.3.0", "1.0.0", "3.0.0", "3.0", true).clusterReachable);
    assertFalse(ClusterVersionService.capabilitiesFrom(
        "4.19.0", "1.2.1", null, null, "2.6").clusterReachable);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubMinimalDetectedCluster(String ocpVersion, String gatewayApiBundle) {
    when(kubernetesClient.genericKubernetesResources(any(ResourceDefinitionContext.class)))
        .thenAnswer(inv -> {
          ResourceDefinitionContext ctx = inv.getArgument(0);
          var op = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
          if ("clusterversions".equals(ctx.getPlural())) {
            GenericKubernetesResource cv = clusterVersion(ocpVersion);
            var resource = mock(io.fabric8.kubernetes.client.dsl.Resource.class);
            when(op.withName("version")).thenReturn(resource);
            when(resource.get()).thenReturn(cv);
            return op;
          }
          if ("clusterserviceversions".equals(ctx.getPlural())) {
            var nsOp = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
            GenericKubernetesResourceList list = new GenericKubernetesResourceList();
            list.setItems(List.of());
            when(op.inNamespace(any(String.class))).thenReturn(nsOp);
            when(nsOp.list()).thenReturn(list);
            return op;
          }
          var nsOp = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
          GenericKubernetesResourceList list = new GenericKubernetesResourceList();
          list.setItems(List.of());
          when(op.inAnyNamespace()).thenReturn(nsOp);
          when(nsOp.list()).thenReturn(list);
          return op;
        });

    var apiextensions = mock(io.fabric8.kubernetes.client.dsl.ApiextensionsAPIGroupDSL.class);
    var v1 = mock(io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL.class);
    var crdOps = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
    var gatewayCrd = new io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition();
    ObjectMeta gatewayMeta = new ObjectMeta();
    gatewayMeta.setName("gatewayclasses.gateway.networking.k8s.io");
    gatewayMeta.setAnnotations(Map.of(
        "gateway.networking.k8s.io/bundle-version",
        gatewayApiBundle.startsWith("v") ? gatewayApiBundle : "v" + gatewayApiBundle));
    gatewayCrd.setMetadata(gatewayMeta);
    when(kubernetesClient.apiextensions()).thenReturn(apiextensions);
    when(apiextensions.v1()).thenReturn(v1);
    when(v1.customResourceDefinitions()).thenReturn(crdOps);
    var authPolicyRes = mock(io.fabric8.kubernetes.client.dsl.Resource.class);
    var gatewayClassRes = mock(io.fabric8.kubernetes.client.dsl.Resource.class);
    when(crdOps.withName("authpolicies.kuadrant.io")).thenReturn(authPolicyRes);
    when(crdOps.withName("gatewayclasses.gateway.networking.k8s.io")).thenReturn(gatewayClassRes);
    when(authPolicyRes.get()).thenReturn(null);
    when(gatewayClassRes.get()).thenReturn(gatewayCrd);
  }

  private static GenericKubernetesResource clusterVersion(String version) {
    GenericKubernetesResource cv = new GenericKubernetesResource();
    cv.setAdditionalProperties(Map.of("status", Map.of("desired", Map.of("version", version))));
    return cv;
  }
}
