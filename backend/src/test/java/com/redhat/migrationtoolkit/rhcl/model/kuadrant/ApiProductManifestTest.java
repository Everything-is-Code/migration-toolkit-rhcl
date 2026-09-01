package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiProductManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesApiProductGeneratorShape() {
        String name = "demo-api";
        ApiProductManifest manifest = new ApiProductManifest(
                "devportal.kuadrant.io/v1alpha1",
                "APIProduct",
                new ManifestMeta(name, "migration-ns", Map.of("app", name, "migrated-from", "3scale"), null),
                new ApiProductSpec(
                        "Demo API",
                        "Migrated from 3scale",
                        "automatic",
                        "Published",
                        new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"),
                        "v1"));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));

        assertEquals("devportal.kuadrant.io/v1alpha1", parsed.get("apiVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("Demo API", spec.get("displayName"));
        assertEquals("Migrated from 3scale", spec.get("description"));
    }

    @Test
    void descriptionWithEmbeddedQuotes_serializesSafely() {
        String name = "demo-api";
        ApiProductManifest manifest = new ApiProductManifest(
                "devportal.kuadrant.io/v1alpha1",
                "APIProduct",
                new ManifestMeta(name, "migration-ns", Map.of("app", name), null),
                new ApiProductSpec(
                        "Demo",
                        "Say \"hello\"",
                        "automatic",
                        "Published",
                        new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"),
                        "v1"));

        YamlAssertions.assertValidYaml(serializer.toYaml(manifest));
    }
}
