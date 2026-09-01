package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("gateway.yaml", generator.outputKey());
        assertEquals("gateway.networking.k8s.io/v1", parsed.get("apiVersion"));
        assertEquals("Gateway", parsed.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("my-api-gateway", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("istio", spec.get("gatewayClassName"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> listeners = (java.util.List<Map<String, Object>>) spec.get("listeners");
        assertEquals(2, listeners.size());
        assertEquals("HTTP", listeners.get(0).get("protocol"));
        assertEquals("HTTPS", listeners.get(1).get("protocol"));
    }

    @Test
    void generate_includesDnsHostnameWhenDnsPolicyEnabled() {
        ApiService service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        ConversionOptions options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> listeners =
                (java.util.List<Map<String, Object>>) ((Map<String, Object>) parsed.get("spec")).get("listeners");

        assertEquals("api.example.com", listeners.get(0).get("hostname"));
        assertEquals("api.example.com", listeners.get(1).get("hostname"));
    }

    @Test
    void generate_withoutDnsPolicy_omitsHostname() {
        ApiService service = GeneratorTestSupport.basicService("Plain API", "plain-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertFalse(yaml.contains("hostname:"));
    }

    @Test
    void generate_sanitizesKebabNameFromSystemName() {
        ApiService service = GeneratorTestSupport.basicService("Weird API", "weird_api.name");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");

        assertNotNull(metadata.get("name"));
        YamlAssertions.assertValidYaml(yaml);
    }

    @Test
    void generate_withoutMigratedFromLabel_omitsLabel() {
        ApiService service = GeneratorTestSupport.basicService("My API", "my-api");
        ConversionOptions options = new ConversionOptions();
        options.includeMigratedFromLabel = false;
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) ((Map<String, Object>) YamlAssertions
                .parse(generator.generate(ctx)).get("metadata")).get("labels");

        assertTrue(labels.containsKey("app"));
        assertFalse(labels.containsKey("migrated-from"));
    }
}
