package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMapGeneratorTest {

    private final ConfigMapGenerator generator = new ConfigMapGenerator();

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("Config API", "config-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesConfigMapWithBackendMetadata() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "config-api", "https://backend.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("configmap.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: v1"));
        assertTrue(yaml.contains("kind: ConfigMap"));
        assertTrue(yaml.contains("name: config-api-config"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("backend-url:"));
        assertTrue(yaml.contains("https://backend.example.com"));
        assertTrue(yaml.contains("service-name: \"config-api\""));
        assertTrue(yaml.contains("original-3scale-service-id: \"42\""));
    }
}
