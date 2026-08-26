package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SecretGeneratorTest {

    @Inject
    SecretGenerator generator;

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("Secret API", "secret-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesOpaqueSecretYaml() {
        ApiService service = GeneratorTestSupport.basicService("Secret API", "secret-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("secret.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: v1"));
        assertTrue(yaml.contains("kind: Secret"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("type: Opaque"));
        assertTrue(yaml.contains("name: secret-api-credentials"));
        assertTrue(yaml.contains("stringData:"));
        assertTrue(yaml.contains("REPLACE_ME"));
    }
}
