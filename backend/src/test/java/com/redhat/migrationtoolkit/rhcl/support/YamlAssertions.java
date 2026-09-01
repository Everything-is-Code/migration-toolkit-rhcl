package com.redhat.migrationtoolkit.rhcl.support;

import com.redhat.migrationtoolkit.rhcl.service.conversion.YamlSerializationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Structural YAML assertions for generator tests.
 */
public final class YamlAssertions {

    private static final ObjectMapper YAML = YamlSerializationConfig.createYamlMapper();

    private YamlAssertions() {
    }

    public static void assertValidYaml(String yaml) {
        try {
            YAML.readTree(requireContent(yaml));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Expected valid YAML but parsing failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String yaml) {
        try {
            Object parsed = YAML.readValue(requireContent(yaml), Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                        "YAML root must be a mapping, got: " + (parsed == null ? "null" : parsed.getClass().getSimpleName()));
            }
            return (Map<String, Object>) parsed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML: " + e.getMessage(), e);
        }
    }

    private static String requireContent(String yaml) {
        if (yaml == null) {
            throw new IllegalArgumentException("YAML input must not be null");
        }
        if (yaml.isBlank()) {
            throw new IllegalArgumentException("YAML input must not be blank");
        }
        return yaml;
    }
}
