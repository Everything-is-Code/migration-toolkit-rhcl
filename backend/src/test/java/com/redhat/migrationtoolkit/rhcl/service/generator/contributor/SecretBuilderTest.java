package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretBuilderTest {

    @Test
    void build_assemblesSecretMetadata() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("client-id", "REPLACE_ME");
        builder.addStringData("client-secret", "REPLACE_ME");

        String yaml = builder.build();
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertEquals("Secret", parsed.get("kind"));
        assertEquals("v1", parsed.get("apiVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("demo-api-credentials", metadata.get("name"));
        assertEquals("ns", metadata.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) parsed.get("stringData");
        assertEquals("REPLACE_ME", stringData.get("client-id"));
        assertEquals("REPLACE_ME", stringData.get("client-secret"));
    }

    @Test
    void build_insertsDiscoveryMarkerBeforeType() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("client-id", "REPLACE_ME");
        builder.setDiscoveryMarker("x-discovery-marker: rhcl-secret-test");

        String yaml = builder.build();
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, String> annotations = (Map<String, String>) ((Map<String, Object>) parsed.get("metadata")).get("annotations");

        assertEquals("rhcl-secret-test", annotations.get("x-discovery-marker"));
    }

    @Test
    void addStringData_duplicateKey_lastWriteWins() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("client-id", "first");
        builder.addStringData("client-id", "second");

        String yaml = builder.build();

        assertTrue(yaml.contains("client-id: second") || yaml.contains("client-id: \"second\""));
        assertTrue(!yaml.contains("first"));
    }

    @Test
    void build_withoutStringData_emitsEmptyStringDataBlock() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-empty");

        String yaml = builder.build();

        assertTrue(yaml.contains("stringData: {}"));
    }

    @Test
    void build_withoutMigratedFromLabel_omitsLabel() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionOptions options = new ConversionOptions();
        options.includeMigratedFromLabel = false;
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, options, new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("client-id", "REPLACE_ME");

        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) ((Map<String, Object>) YamlAssertions
                .parse(builder.build()).get("metadata")).get("labels");

        assertTrue(labels.containsKey("app"));
        assertFalse(labels.containsKey("migrated-from"));
    }

    @Test
    void build_malformedDiscoveryMarker_isIgnored() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.setDiscoveryMarker("not-a-valid-marker");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) YamlAssertions.parse(builder.build()).get("metadata");

        assertNull(metadata.get("annotations"));
    }

    @Test
    void build_discoveryMarkerValueMayContainColons() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.setDiscoveryMarker("x-discovery-marker: rhcl:secret:test");

        @SuppressWarnings("unchecked")
        Map<String, String> annotations = (Map<String, String>) ((Map<String, Object>) YamlAssertions
                .parse(builder.build()).get("metadata")).get("annotations");

        assertEquals("rhcl:secret:test", annotations.get("x-discovery-marker"));
    }

    @Test
    void injectYamlComment_fallsBackBeforeTypeWhenStringDataAbsent() {
        String yaml = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: demo
                type: Opaque
                """;
        String prefix = "# WARNING: test\n";

        String result = SecretBuilder.injectYamlCommentBeforeStringData(yaml, prefix);

        assertTrue(result.contains(prefix));
        assertTrue(result.indexOf(prefix) < result.indexOf("type:"));
    }

    @Test
    void injectEmptyStringData_handlesQuotedOpaqueType() {
        String yaml = """
                apiVersion: v1
                kind: Secret
                type: "Opaque"
                """;

        String result = SecretBuilder.injectEmptyStringData(yaml);

        assertTrue(result.contains("stringData: {}"));
    }

    @Test
    void build_returnsEmptyWhenSecretNotStarted() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());

        assertEquals("", new SecretBuilder(ctx).build());
    }

    @Test
    void build_noArg_usesDefaultSerializer() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("token", "value");

        assertTrue(builder.build().contains("token"));
    }

    @Test
    void beginOpaqueSecret_noOpWhenAlreadyStarted() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("first-name");
        builder.beginOpaqueSecret("second-name");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) YamlAssertions.parse(builder.build()).get("metadata");

        assertEquals("first-name", metadata.get("name"));
    }

    @Test
    void addLabel_addsCustomLabel() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addLabel("auth-type", "oauth2");

        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) ((Map<String, Object>) YamlAssertions
                .parse(builder.build()).get("metadata")).get("labels");

        assertEquals("oauth2", labels.get("auth-type"));
    }

    @Test
    void addStringData_nullValue_becomesEmptyString() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("optional", null);

        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) YamlAssertions.parse(builder.build()).get("stringData");

        assertEquals("", stringData.get("optional"));
    }

    @Test
    void addStringData_beforeBegin_throws() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);

        assertThrows(IllegalStateException.class, () -> builder.addStringData("k", "v"));
    }

    @Test
    void setYamlCommentPrefix_null_isIgnored() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.addStringData("token", "value");
        builder.setYamlCommentPrefix(null);

        assertFalse(builder.build().startsWith("#"));
    }

    @Test
    void build_emptyValueDiscoveryMarker_isIgnored() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-credentials");
        builder.setDiscoveryMarker("marker-key:   ");

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) YamlAssertions.parse(builder.build()).get("metadata");

        assertNull(metadata.get("annotations"));
    }

    @Test
    void build_injectsEmptyStringDataWhenSerializerOmitsBlock() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-empty");
        ManifestSerializer serializer = new ManifestSerializer() {
            @Override
            public String toYaml(Object manifest) {
                return "apiVersion: v1\nkind: Secret\ntype: Opaque\n";
            }
        };

        assertTrue(builder.build(serializer).contains("stringData: {}"));
    }

    @Test
    void build_injectsCommentWhenSerializerOmitsStringData() {
        ApiService service = new ApiService();
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.beginOpaqueSecret("demo-api-empty");
        builder.setYamlCommentPrefix("# WARNING: test\n");
        ManifestSerializer serializer = new ManifestSerializer() {
            @Override
            public String toYaml(Object manifest) {
                return "apiVersion: v1\nkind: Secret\ntype: Opaque\n";
            }
        };

        String yaml = builder.build(serializer);

        assertTrue(yaml.contains("# WARNING: test"));
        assertTrue(yaml.indexOf("# WARNING: test") < yaml.indexOf("stringData:"));
    }

    @Test
    void injectYamlComment_appendsWhenNoAnchors() {
        String result = SecretBuilder.injectYamlCommentBeforeStringData("apiVersion: v1\n", "# NOTE\n");

        assertEquals("apiVersion: v1\n# NOTE\n", result);
    }

    @Test
    void injectEmptyStringData_noType_appendsAtEnd() {
        String result = SecretBuilder.injectEmptyStringData("apiVersion: v1\nkind: Secret\n");

        assertTrue(result.endsWith("stringData: {}\n"));
    }

    @Test
    void injectEmptyStringData_noTrailingNewlineAfterType() {
        String result = SecretBuilder.injectEmptyStringData("type: Opaque");

        assertTrue(result.contains("stringData: {}"));
    }

    @Test
    void injectEmptyStringData_singleQuotedOpaqueType() {
        String result = SecretBuilder.injectEmptyStringData("type: 'Opaque'\n");

        assertTrue(result.contains("stringData: {}"));
    }
}
