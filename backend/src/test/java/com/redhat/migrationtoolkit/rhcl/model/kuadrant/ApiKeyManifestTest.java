package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesApiKeyGeneratorShape() {
        String name = "demo-api";
        ApiKeyManifest manifest = new ApiKeyManifest(
                "devportal.kuadrant.io/v1alpha1",
                "APIKey",
                new ManifestMeta(
                        name + "-api-key",
                        "migration-ns",
                        Map.of("app", name, "migrated-from", "3scale"),
                        null),
                new ApiKeySpec(
                        new ApiProductRef(name),
                        "basic",
                        new RequestedBy("admin@example.com", "admin"),
                        new SecretRef(name + "-api-key")));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));

        assertEquals("APIKey", parsed.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> secretRef = (Map<String, Object>) spec.get("secretRef");
        assertEquals("demo-api-api-key", secretRef.get("name"));
    }
}
