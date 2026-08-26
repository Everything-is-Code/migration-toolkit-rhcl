package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void applies_returnsTrue_whenApiKeyAuthenticationPresent() {
        ApiService service = GeneratorTestSupport.basicService("Key API", "key-api");
        service.authentication = GeneratorTestSupport.apiKeyAuth();
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenAuthenticationIsNotApiKey() {
        ApiService service = GeneratorTestSupport.basicService("JWT API", "jwt-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesApiKeyYaml() {
        ApiService service = GeneratorTestSupport.basicService("Key API", "key-api");
        service.authentication = GeneratorTestSupport.apiKeyAuth();
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("apikey.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: devportal.kuadrant.io/v1alpha1"));
        assertTrue(yaml.contains("kind: APIKey"));
        assertTrue(yaml.contains("name: key-api-api-key"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("apiProductRef:"));
        assertTrue(yaml.contains("name: key-api"));
        assertTrue(yaml.contains("secretRef:"));
    }
}
