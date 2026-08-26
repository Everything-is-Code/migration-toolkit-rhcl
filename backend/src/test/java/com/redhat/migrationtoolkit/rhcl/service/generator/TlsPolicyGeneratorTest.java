package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TlsPolicyGeneratorTest {

    @Inject
    TlsPolicyGenerator generator;

    @Test
    void applies_returnsTrue_whenTlsPolicyOptInEnabled() {
        ApiService service = GeneratorTestSupport.basicService("TLS API", "tls-api");
        ConversionOptions options = new ConversionOptions();
        options.includeTlsPolicy = true;
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenTlsPolicyOptInDisabled() {
        ApiService service = GeneratorTestSupport.basicService("TLS API", "tls-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesTlsPolicyWithIssuerRef() {
        ApiService service = GeneratorTestSupport.basicService("TLS API", "tls-api");
        ConversionOptions options = new ConversionOptions();
        options.includeTlsPolicy = true;
        options.tlsIssuerKind = "ClusterIssuer";
        options.tlsIssuerName = "letsencrypt-staging";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("tlspolicy.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(yaml.contains("kind: TLSPolicy"));
        assertTrue(yaml.contains("name: tls-api-tls-policy"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("targetRef:"));
        assertTrue(yaml.contains("kind: Gateway"));
        assertTrue(yaml.contains("name: tls-api-gateway"));
        assertTrue(yaml.contains("issuerRef:"));
        assertTrue(yaml.contains("kind: ClusterIssuer"));
        assertTrue(yaml.contains("name: letsencrypt-staging"));
    }
}
