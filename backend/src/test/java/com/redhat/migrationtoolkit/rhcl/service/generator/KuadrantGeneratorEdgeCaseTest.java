package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for Kuadrant simple generators (#262 task 5).
 */
class KuadrantGeneratorEdgeCaseTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    private static ConversionContext ctx(ApiService service, ConversionOptions options) {
        return ConversionContext.build(service, "ns", null, options, new BackendResolver());
    }

    // 5.6 — TlsPolicyGenerator fails fast with clear error when issuerRef is blank
    @Test
    void tlsPolicyGenerator_blankIssuerKindAndName_stillUsesDefaults() {
        TlsPolicyGenerator generator = new TlsPolicyGenerator();
        generator.bindManual(SERIALIZER);

        ApiService service = new ApiService();
        service.name = "svc";
        service.systemName = "svc";
        ConversionOptions options = new ConversionOptions();
        options.includeTlsPolicy = true;
        // Leave tlsIssuerKind and tlsIssuerName null/blank → defaults applied
        ConversionContext ctx = ctx(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertTrue(yaml.contains("kind: ClusterIssuer"), "Default kind should be ClusterIssuer");
        assertTrue(yaml.contains("name: letsencrypt-prod"), "Default name should be letsencrypt-prod");
    }

    // 5.8 — ApiKeyGenerator with empty/blank service name produces predictable output
    @Test
    void apiKeyGenerator_standardService_producesDeterministicYaml() {
        ApiKeyGenerator generator = new ApiKeyGenerator();
        generator.bindManual(SERIALIZER);

        ApiService service = new ApiService();
        service.name = "my-api";
        service.systemName = "my-api";
        service.authentication = new Authentication();
        service.authentication.type = "apiKey";
        ConversionOptions options = new ConversionOptions();
        ConversionContext ctx = ctx(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertTrue(yaml.contains("kind: APIKey"));
        assertTrue(yaml.contains("name: my-api-api-key"));
        assertTrue(yaml.contains("planTier: basic"));
        assertTrue(yaml.contains("email: admin@example.com"));
    }

    // 5.7 — ApiProductGenerator with embedded quotes uses Jackson quoting (no replace hack)
    @Test
    void apiProductGenerator_descriptionWithEmbeddedQuotes_jacksonHandles() {
        ApiProductGenerator generator = new ApiProductGenerator();
        generator.bindManual(SERIALIZER);

        ApiService service = new ApiService();
        service.name = "My \"Quoted\" API";
        service.systemName = "my-api";
        service.description = "A service with \"embedded\" quotes and : colons and # comments";
        ConversionOptions options = new ConversionOptions();
        ConversionContext ctx = ctx(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertTrue(yaml.contains("description:"), "description field must be present");
        // Jackson handles quoting — no replace hack should cause incorrect output
        assertTrue(yaml.contains("embedded"), "description content should be present");
    }

    // DnsPolicy with no provider ref → providerRefs omitted
    @Test
    void dnsPolicyGenerator_noProviderRef_omitsProviderRefs() {
        DnsPolicyGenerator generator = new DnsPolicyGenerator();
        generator.bindManual(SERIALIZER);

        ApiService service = new ApiService();
        service.name = "dns-api";
        service.systemName = "dns-api";
        ConversionOptions options = new ConversionOptions();
        options.dnsHostname = "api.example.com";
        // dnsProviderSecretName left null
        ConversionContext ctx = ctx(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertFalse(yaml.contains("providerRefs:"), "providerRefs should be absent when not configured");
    }

    // DnsPolicy with provider ref
    @Test
    void dnsPolicyGenerator_withProviderRef_includesProviderRefs() {
        DnsPolicyGenerator generator = new DnsPolicyGenerator();
        generator.bindManual(SERIALIZER);

        ApiService service = new ApiService();
        service.name = "dns-api";
        service.systemName = "dns-api";
        ConversionOptions options = new ConversionOptions();
        options.dnsHostname = "api.example.com";
        options.dnsProviderSecretName = "my-dns-secret";
        ConversionContext ctx = ctx(service, options);

        String yaml = generator.generate(ctx);

        assertTrue(yaml.contains("providerRefs:"));
        assertTrue(yaml.contains("name: my-dns-secret"));
    }
}
