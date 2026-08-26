package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DestinationRuleGeneratorTest {

    private final DestinationRuleGenerator generator = new DestinationRuleGenerator();

    @Test
    void applies_returnsTrue_whenExternalBackendPresent() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "dr-api", "https://secure.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenOnlyInternalBackend() {
        ApiService service = GeneratorTestSupport.basicService("Internal API", "internal-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesDestinationRuleWithTlsPolicy() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "dr-api", "https://secure.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("destinationrule.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: DestinationRule"));
        assertTrue(yaml.contains("apiVersion: networking.istio.io/v1alpha3"));
        assertTrue(yaml.contains("name: dr-api-backend-tls"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("host: secure.example.com"));
        assertTrue(yaml.contains("trafficPolicy:"));
        assertTrue(yaml.contains("tls:"));
    }
}
