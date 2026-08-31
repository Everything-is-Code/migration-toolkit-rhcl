package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.IssuerRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicySpec;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ManifestSerializerTest {

    @Inject
    ManifestSerializer serializer;

    @Test
    void toYaml_fabric8Secret_producesValidYamlWithoutNullNoise() {
        String yaml = serializer.toYaml(new SecretBuilder()
                .withNewMetadata()
                .withName("demo-secret")
                .withNamespace("migration-ns")
                .endMetadata()
                .addToStringData("key", "value")
                .build());

        YamlAssertions.assertValidYaml(yaml);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        assertEquals("Secret", parsed.get("kind"));
        assertFalse(yaml.contains(": null"));
    }

    @Test
    void toYaml_kuadrantRecord_producesExpectedFieldOrderAndContent() {
        TlsPolicyManifest manifest = new TlsPolicyManifest(
                "kuadrant.io/v1",
                "TLSPolicy",
                new ManifestMeta("demo-tls-policy", "migration-ns", Map.of("app", "demo"), null),
                new TlsPolicySpec(
                        new TargetRef("gateway.networking.k8s.io", "Gateway", "demo-gateway"),
                        new IssuerRef("cert-manager.io", "ClusterIssuer", "letsencrypt-prod")));

        String yaml = serializer.toYaml(manifest);

        YamlAssertions.assertValidYaml(yaml);
        assertTrue(yaml.indexOf("apiVersion:") < yaml.indexOf("kind:"));
        assertTrue(yaml.indexOf("kind:") < yaml.indexOf("metadata:"));
        assertTrue(yaml.indexOf("metadata:") < yaml.indexOf("spec:"));
        assertFalse(yaml.contains(": null"));
    }

    @Test
    void toYaml_null_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> serializer.toYaml(null));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void toYaml_specialYamlCharacters_areQuotedOrEscaped() {
        ManifestMeta meta = new ManifestMeta(
                "demo:api#1",
                "migration-ns",
                Map.of("note", "value: with # and {braces}", "multiline", "line1\nline2"),
                null);

        String yaml = serializer.toYaml(meta);

        YamlAssertions.assertValidYaml(yaml);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        assertEquals("demo:api#1", parsed.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) parsed.get("labels");
        assertEquals("value: with # and {braces}", labels.get("note"));
        assertEquals("line1\nline2", labels.get("multiline"));
    }

    @Test
    void toYaml_allNullOptionalFields_omitsNullLines() {
        ManifestMeta meta = new ManifestMeta("demo", "ns", Map.of("app", "demo"), null);
        String yaml = serializer.toYaml(meta);
        assertFalse(yaml.contains("annotations:"));
        assertFalse(yaml.contains(": null"));
    }
}
