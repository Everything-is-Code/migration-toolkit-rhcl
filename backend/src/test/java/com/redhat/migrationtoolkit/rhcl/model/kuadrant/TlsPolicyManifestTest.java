package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TlsPolicyManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesTlsPolicyGeneratorShape() {
        String name = "demo-api";
        TlsPolicyManifest manifest = new TlsPolicyManifest(
                "kuadrant.io/v1",
                "TLSPolicy",
                new ManifestMeta(
                        name + "-tls-policy",
                        "migration-ns",
                        Map.of("app", name, "migrated-from", "3scale"),
                        null),
                new TlsPolicySpec(
                        new TargetRef("gateway.networking.k8s.io", "Gateway", name + "-gateway"),
                        new IssuerRef("cert-manager.io", "ClusterIssuer", "letsencrypt-prod")));

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));

        assertEquals("kuadrant.io/v1", parsed.get("apiVersion"));
        assertEquals("TLSPolicy", parsed.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("demo-api-tls-policy", metadata.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> issuerRef = (Map<String, Object>) spec.get("issuerRef");
        assertEquals("letsencrypt-prod", issuerRef.get("name"));
    }
}
