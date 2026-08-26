package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceEntryGeneratorTest {

    private final ServiceEntryGenerator generator = new ServiceEntryGenerator();

    @Test
    void applies_returnsTrue_whenExternalBackendPresent() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "ext-api", "https://api.external.example.com");
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
    void generate_producesServiceEntryAndExternalNameService() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "ext-api", "https://api.external.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("serviceentry.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: ServiceEntry"));
        assertTrue(yaml.contains("apiVersion: networking.istio.io/v1alpha3"));
        assertTrue(yaml.contains("name: ext-api-external"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("hosts:"));
        assertTrue(yaml.contains("api.external.example.com"));
        assertTrue(yaml.contains("kind: Service"));
        assertTrue(yaml.contains("type: ExternalName"));
    }
}
