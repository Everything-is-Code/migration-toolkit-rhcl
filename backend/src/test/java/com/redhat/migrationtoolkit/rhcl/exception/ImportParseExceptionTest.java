package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ImportParseExceptionTest {

  @Test
  void messageConstructor_setsDefaults() {
    var ex = new ImportParseException("invalid zip");
    assertEquals("invalid zip", ex.getMessage());
    assertEquals("IMPORT_PARSE_ERROR", ex.getCode());
    assertEquals(400, ex.getStatus());
  }

  @Test
  void messageAndCauseConstructor_setsCause() {
    var cause = new java.io.IOException("corrupt");
    var ex = new ImportParseException("parse failed", cause);
    assertEquals("parse failed", ex.getMessage());
    assertEquals("IMPORT_PARSE_ERROR", ex.getCode());
    assertSame(cause, ex.getCause());
  }

  @Test
  void noYaml_factory_setsCodeAndMessage() {
    var ex = ImportParseException.noYaml();
    assertEquals("No YAML files found in ZIP", ex.getMessage());
    assertEquals("IMPORT_NO_YAML", ex.getCode());
    assertEquals(400, ex.getStatus());
  }
}
