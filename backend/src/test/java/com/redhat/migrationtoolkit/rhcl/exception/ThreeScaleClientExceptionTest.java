package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ThreeScaleClientExceptionTest {

  @Test
  void messageConstructor_setsDefaults() {
    var ex = new ThreeScaleClientException("upstream down");
    assertEquals("upstream down", ex.getMessage());
    assertEquals("THREESCALE_CLIENT_ERROR", ex.getCode());
    assertEquals(502, ex.getStatus());
  }

  @Test
  void messageAndCauseConstructor_setsCause() {
    var cause = new RuntimeException("timeout");
    var ex = new ThreeScaleClientException("request failed", cause);
    assertEquals("request failed", ex.getMessage());
    assertEquals("THREESCALE_CLIENT_ERROR", ex.getCode());
    assertSame(cause, ex.getCause());
  }

  @Test
  void customCodeConstructor_setsCode() {
    var ex = new ThreeScaleClientException("THREESCALE_AUTH_FAILED", "Invalid access token");
    assertEquals("THREESCALE_AUTH_FAILED", ex.getCode());
    assertEquals("Invalid access token", ex.getMessage());
    assertEquals(502, ex.getStatus());
  }
}
