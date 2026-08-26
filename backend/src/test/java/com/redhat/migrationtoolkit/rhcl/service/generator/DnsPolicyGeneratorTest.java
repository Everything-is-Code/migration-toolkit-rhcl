package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DnsPolicyGeneratorTest {

    @Inject
    DnsPolicyGenerator generator;

    @Test
    void applies_returnsTrue_whenDnsHostnameProvided() {
        ApiService service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        ConversionOptions options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenDnsPolicyDisabled() {
        ApiService service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesDnsPolicyWithTargetRef() {
        ApiService service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        ConversionOptions options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        options.dnsProviderSecretName = "dns-credentials";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("dnspolicy.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(yaml.contains("kind: DNSPolicy"));
        assertTrue(yaml.contains("name: dns-api-dns-policy"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("targetRef:"));
        assertTrue(yaml.contains("kind: Gateway"));
        assertTrue(yaml.contains("name: dns-api-gateway"));
        assertTrue(yaml.contains("providerRefs:"));
        assertTrue(yaml.contains("name: dns-credentials"));
    }
}
