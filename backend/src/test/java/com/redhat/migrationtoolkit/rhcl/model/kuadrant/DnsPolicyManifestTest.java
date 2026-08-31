package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DnsPolicyManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_withoutProviderRefs_omitsOptionalBlock() {
        String name = "demo-api";
        DnsPolicyManifest manifest = minimalManifest(name, null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertFalse(spec.containsKey("providerRefs"));
    }

    @Test
    void serialization_withProviderRefs_matchesGeneratorShape() {
        String name = "demo-api";
        DnsPolicyManifest manifest = minimalManifest(name, List.of(new ProviderRef("dns-secret")));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providerRefs = (List<Map<String, Object>>) spec.get("providerRefs");
        assertNotNull(providerRefs);
        assertEquals("dns-secret", providerRefs.get(0).get("name"));
    }

    private static DnsPolicyManifest minimalManifest(String name, List<ProviderRef> providerRefs) {
        return new DnsPolicyManifest(
                "kuadrant.io/v1",
                "DNSPolicy",
                new ManifestMeta(
                        name + "-dns-policy",
                        "migration-ns",
                        Map.of("app", name, "migrated-from", "3scale"),
                        null),
                new DnsPolicySpec(
                        new TargetRef("gateway.networking.k8s.io", "Gateway", name + "-gateway"),
                        providerRefs));
    }
}
