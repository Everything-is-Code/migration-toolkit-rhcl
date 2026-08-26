package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationExceptionTest {

  @Test
  void messageConstructor_setsDefaults() {
    var ex = new ValidationException("field required");
    assertEquals("field required", ex.getMessage());
    assertEquals("VALIDATION_FAILED", ex.getCode());
    assertEquals(400, ex.getStatus());
    assertEquals(Map.of(), ex.getDetails());
  }

  @Test
  void messageAndDetailsConstructor_setsDetails() {
    var details = Map.<String, Object>of("name", "must not be blank");
    var ex = new ValidationException("validation failed", details);
    assertEquals("validation failed", ex.getMessage());
    assertEquals("VALIDATION_FAILED", ex.getCode());
    assertEquals(details, ex.getDetails());
  }
}
