package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotFoundExceptionTest {

  @Test
  void constructor_setsCodeMessageAndStatus() {
    var ex = new NotFoundException("SERVICE_NOT_FOUND", "Service 42 not found");
    assertEquals("SERVICE_NOT_FOUND", ex.getCode());
    assertEquals("Service 42 not found", ex.getMessage());
    assertEquals(404, ex.getStatus());
  }
}
