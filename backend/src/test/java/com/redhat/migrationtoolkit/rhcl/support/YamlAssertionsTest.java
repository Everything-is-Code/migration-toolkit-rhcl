package com.redhat.migrationtoolkit.rhcl.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlAssertionsTest {

    @Test
    void assertValidYaml_acceptsWellFormedDocument() {
        YamlAssertions.assertValidYaml("apiVersion: v1\nkind: ConfigMap\n");
    }

    @Test
    void assertValidYaml_malformedYaml_throwsAssertionErrorWithCause() {
        AssertionError error = assertThrows(AssertionError.class, () -> YamlAssertions.assertValidYaml("apiVersion: [unclosed"));
        assertTrue(error.getMessage().contains("valid YAML"));
        assertNotNullCause(error);
    }

    @Test
    void assertValidYaml_badIndentation_throwsAssertionError() {
        AssertionError error = assertThrows(AssertionError.class, () -> YamlAssertions.assertValidYaml(
                "parent:\n  child: value\n child2: value"));
        assertTrue(error.getMessage().contains("valid YAML"));
    }

    @Test
    void parse_null_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> YamlAssertions.parse(null));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void parse_blank_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> YamlAssertions.parse("   "));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void parse_scalarRoot_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> YamlAssertions.parse("just-a-scalar"));
        assertTrue(ex.getMessage().contains("mapping"));
    }

    @Test
    void parse_listRoot_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> YamlAssertions.parse("- one\n- two\n"));
        assertTrue(ex.getMessage().contains("mapping"));
    }

    @Test
    void parse_mapRoot_returnsTopLevelMap() {
        Map<String, Object> parsed = YamlAssertions.parse("apiVersion: v1\nkind: Secret\n");
        assertEquals("v1", parsed.get("apiVersion"));
        assertEquals("Secret", parsed.get("kind"));
    }

    private static void assertNotNullCause(AssertionError error) {
        assertTrue(error.getCause() != null || error.getMessage().contains("parsing failed"));
    }
}
