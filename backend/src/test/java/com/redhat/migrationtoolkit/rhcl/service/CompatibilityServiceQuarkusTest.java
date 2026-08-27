package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityItem;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JaCoCo records {@link CompatibilityService} clusterReachable gating only through
 * Quarkus-instrumented beans. Plain JUnit suites assert behavior but do not satisfy
 * patch coverage on the new unreachable-cluster paths.
 */
@QuarkusTest
class CompatibilityServiceQuarkusTest {

  private static final Set<String> EMPTY_POLICIES = Set.of();

  @Inject
  CompatibilityService service;

  @Test
  void check_unreachableCluster_emitsClusterConnectionNotKuadrant() {
    ApiService svc = basicService();
    svc.authentication = auth("jwt");
    svc.policies = List.of(enabledPolicy("edge_limiting"));
    ClusterCapabilities caps = new ClusterCapabilities();
    caps.clusterReachable = false;
    caps.kuadrantPresent = false;
    caps.timeoutsSupported = true;

    CompatibilityResult result = service.check(svc, Set.of("Edge Limiting"), caps);
    CompatibilityItem connection = result.items.stream()
        .filter(i -> "clusterReachable".equals(i.capability))
        .findFirst()
        .orElseThrow();
    assertEquals("Cluster connection", connection.name);
    assertEquals("WARNING", connection.status);
    assertTrue(connection.message.contains("oc login"));
    assertEquals("OpenShift cluster reachable from backend", connection.requiredVersion);
    assertTrue(result.items.stream().noneMatch(i -> "kuadrantPresent".equals(i.capability)));
  }

  @Test
  void check_unreachableCluster_skipsCorsNativeCapabilityWarning() {
    ApiService svc = basicService();
    svc.authentication = auth("jwt");
    svc.policies = List.of(enabledPolicy("cors"));
    ClusterCapabilities caps = new ClusterCapabilities();
    caps.clusterReachable = false;
    caps.corsNative = false;
    caps.timeoutsSupported = true;

    CompatibilityResult result = service.check(svc, Set.of("CORS Request Handling"), caps);
    assertTrue(result.items.stream().anyMatch(i -> "clusterReachable".equals(i.capability)));
    assertTrue(result.items.stream().noneMatch(i -> "corsNative".equals(i.capability)));
  }

  @Test
  void check_corsWithoutNativeCapability_whenReachable_warnsFallback() {
    ApiService svc = basicService();
    svc.authentication = auth("jwt");
    svc.policies = List.of(enabledPolicy("cors"));
    ClusterCapabilities caps = new ClusterCapabilities();
    caps.clusterReachable = true;
    caps.corsNative = false;
    caps.timeoutsSupported = true;

    CompatibilityResult result = service.check(svc, Set.of("CORS Request Handling"), caps);
    assertTrue(result.items.stream().anyMatch(i -> "corsNative".equals(i.capability)));
  }

  @Test
  void check_unreachableCluster_skipsOssmMismatchWarning() {
    ApiService svc = basicService();
    svc.authentication = auth("jwt");
    ClusterCapabilities caps = new ClusterCapabilities();
    caps.clusterReachable = false;
    caps.ossmPresent = true;
    caps.ossmMatchesOcp = false;
    caps.timeoutsSupported = true;

    CompatibilityResult result = service.check(svc, EMPTY_POLICIES, caps);
    assertTrue(result.items.stream().anyMatch(i -> "clusterReachable".equals(i.capability)));
    assertTrue(result.items.stream().noneMatch(i -> "ossmMatchesOcp".equals(i.capability)));
  }

  private static ApiService basicService() {
    ApiService svc = new ApiService();
    svc.id = "svc-1";
    svc.name = "Test Service";
    return svc;
  }

  private static Authentication auth(String type) {
    Authentication auth = new Authentication();
    auth.type = type;
    return auth;
  }

  private static Policy enabledPolicy(String name) {
    Policy policy = new Policy();
    policy.name = name;
    policy.enabled = true;
    return policy;
  }
}
