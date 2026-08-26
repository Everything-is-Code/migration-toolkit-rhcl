package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClusterApplyExceptionTest {

  @Test
  void messageOnlyConstructor_setsDefaults() {
    var ex = new ClusterApplyException("apply failed");
    assertEquals("apply failed", ex.getMessage());
    assertEquals("APPLY_FAILED", ex.getCode());
    assertEquals(500, ex.getStatus());
  }

  @Test
  void messageAndCauseConstructor_setsCause() {
    var cause = new RuntimeException("kube");
    var ex = new ClusterApplyException("apply failed", cause);
    assertEquals("apply failed", ex.getMessage());
    assertEquals("APPLY_FAILED", ex.getCode());
    assertSame(cause, ex.getCause());
  }

  @Test
  void messageCauseAndDetailsConstructor_setsDetails() {
    var cause = new RuntimeException("kube");
    var details = Map.<String, Object>of("resource", "gateway.yaml");
    var ex = new ClusterApplyException("apply failed", cause, details);
    assertEquals(details, ex.getDetails());
    assertSame(cause, ex.getCause());
  }

  @Test
  void customCodeConstructor_setsCode() {
    var ex = new ClusterApplyException("PARTIAL_APPLY", "some resources failed");
    assertEquals("PARTIAL_APPLY", ex.getCode());
    assertEquals("some resources failed", ex.getMessage());
    assertEquals(500, ex.getStatus());
  }
}
