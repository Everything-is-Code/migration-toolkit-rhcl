package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestMetaTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void roundTrip_preservesCoreFields() {
        ManifestMeta meta = new ManifestMeta(
                "demo-api",
                "migration-ns",
                Map.of("app", "demo-api", "migrated-from", "3scale"),
                Map.of("note", "test"));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(meta));

        assertEquals("demo-api", parsed.get("name"));
        assertEquals("migration-ns", parsed.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) parsed.get("labels");
        assertEquals("demo-api", labels.get("app"));
        @SuppressWarnings("unchecked")
        Map<String, String> annotations = (Map<String, String>) parsed.get("annotations");
        assertEquals("test", annotations.get("note"));
    }

    @Test
    void nullAnnotations_omittedFromYaml() {
        ManifestMeta meta = new ManifestMeta("demo-api", "ns", Map.of("app", "demo-api"), null);
        String yaml = serializer.toYaml(meta);
        assertFalse(yaml.contains("annotations:"));
        assertFalse(yaml.contains("annotations: null"));
    }

    @Test
    void emptyLabels_serializesAsEmptyMap() {
        ManifestMeta meta = new ManifestMeta("demo-api", "ns", Map.of(), null);
        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(meta));
        assertNotNull(parsed.get("labels"));
        assertTrue(((Map<?, ?>) parsed.get("labels")).isEmpty());
    }

    @Test
    void nullName_omittedFromYaml() {
        ManifestMeta meta = new ManifestMeta(null, "ns", Map.of("app", "demo-api"), null);
        String yaml = serializer.toYaml(meta);
        assertFalse(yaml.contains("name:"));
    }
}
