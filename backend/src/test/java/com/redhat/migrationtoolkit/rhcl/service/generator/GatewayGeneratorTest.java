package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayGeneratorTest {

    private final GatewayGenerator generator = new GatewayGenerator();

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("My API", "my-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_forRegistryDiscoveryService() {
        ApiService service = GeneratorTestSupport.basicService("Discovery", RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesGatewayYamlWithListeners() {
        ApiService service = GeneratorTestSupport.basicService("My API", "my-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("gateway.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: gateway.networking.k8s.io/v1"));
        assertTrue(yaml.contains("kind: Gateway"));
        assertTrue(yaml.contains("name: my-api-gateway"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("gatewayClassName: istio"));
        assertTrue(yaml.contains("listeners:"));
        assertTrue(yaml.contains("protocol: HTTP"));
        assertTrue(yaml.contains("protocol: HTTPS"));
    }

    @Test
    void generate_includesDnsHostnameWhenDnsPolicyEnabled() {
        ApiService service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        ConversionOptions options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);

        assertTrue(yaml.contains("hostname: api.example.com"));
    }
}
