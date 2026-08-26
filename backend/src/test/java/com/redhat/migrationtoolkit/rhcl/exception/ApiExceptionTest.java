package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApiExceptionTest {

  private static final class TestApiException extends ApiException {
    TestApiException(String code, int status, String message) {
      super(code, status, message);
    }

    TestApiException(String code, int status, String message, Throwable cause) {
      super(code, status, message, cause);
    }

    TestApiException(String code, int status, String message, Map<String, Object> details, Throwable cause) {
      super(code, status, message, details, cause);
    }
  }

  @Test
  void constructor_setsMessageAndCode() {
    var ex = new TestApiException("TEST_CODE", 400, "test message");
    assertEquals("test message", ex.getMessage());
    assertEquals("TEST_CODE", ex.getCode());
    assertEquals(400, ex.getStatus());
    assertEquals(Map.of(), ex.getDetails());
  }

  @Test
  void constructor_withCause_setsCause() {
    var cause = new RuntimeException("root");
    var ex = new TestApiException("TEST_CODE", 500, "wrapped", cause);
    assertEquals("wrapped", ex.getMessage());
    assertEquals("TEST_CODE", ex.getCode());
    assertSame(cause, ex.getCause());
  }

  @Test
  void constructor_withDetailsAndNullDetailsMap_usesEmptyMap() {
    var details = Map.<String, Object>of("key", "value");
    var ex = new TestApiException("TEST_CODE", 400, "with details", details, null);
    assertEquals(details, ex.getDetails());
  }

  @Test
  void constructor_withNullDetails_usesEmptyMap() {
    var ex = new TestApiException("TEST_CODE", 400, "no details", null, null);
    assertEquals(Map.of(), ex.getDetails());
  }
}
