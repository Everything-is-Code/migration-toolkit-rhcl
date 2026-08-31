package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitPolicyManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesRateLimitSupportEnvelope() {
        String name = "demo-api";
        Map<String, LimitDefinition> limits = new LinkedHashMap<>();
        limits.put("global", new LimitDefinition(List.of(new Rate(100, "60s"))));
        limits.put("edge_fixed_window_1", new LimitDefinition(List.of(new Rate(10, "30s"))));

        RateLimitPolicyManifest manifest = envelope(name, limits);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> limitsNode = (Map<String, Object>) spec.get("limits");
        assertNotNull(limitsNode.get("global"));
        assertNotNull(limitsNode.get("edge_fixed_window_1"));
    }

    @Test
    void zeroRateAndLimit_serializeAsZero() {
        RateLimitPolicyManifest manifest = envelope(
                "demo-api",
                Map.of("zero-limit", new LimitDefinition(List.of(new Rate(0, "1s")))));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> limitsNode = (Map<String, Object>) spec.get("limits");
        @SuppressWarnings("unchecked")
        Map<String, Object> zeroLimit = (Map<String, Object>) limitsNode.get("zero-limit");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rates = (List<Map<String, Object>>) zeroLimit.get("rates");
        assertEquals(0, rates.get(0).get("limit"));
    }

    @Test
    void minimalRequiredFields_onlyEnvelopePresent() {
        String name = "demo-api";
        RateLimitPolicyManifest manifest = envelope(name, Map.of());

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        assertEquals("kuadrant.io/v1", parsed.get("apiVersion"));
        assertEquals("RateLimitPolicy", parsed.get("kind"));
    }

    private static RateLimitPolicyManifest envelope(String name, Map<String, LimitDefinition> limits) {
        return new RateLimitPolicyManifest(
                "kuadrant.io/v1",
                "RateLimitPolicy",
                new ManifestMeta(
                        name + "-ratelimit",
                        "migration-ns",
                        Map.of("app", name, "migrated-from", "3scale"),
                        null),
                new RateLimitPolicySpec(
                        new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"),
                        limits));
    }
}
