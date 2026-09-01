package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.DefaultCredentialsSecretContributor;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("secret.yaml", generator.outputKey());
        assertEquals("v1", parsed.get("apiVersion"));
        assertEquals("Secret", parsed.get("kind"));
        assertEquals("Opaque", parsed.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("secret-api-credentials", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) parsed.get("stringData");
        assertTrue(stringData.containsKey("client-id"));
        assertTrue(stringData.containsValue("REPLACE_ME"));
    }

    @Test
    void manualBinding_generatesSecretWithoutCdi() {
        SecretGenerator manual = new SecretGenerator();
        manual.bindManual(new ManifestSerializer());
        manual.bindManualContributors(List.of(new DefaultCredentialsSecretContributor()));
        ApiService service = GeneratorTestSupport.basicService("Manual Secret", "manual-secret");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = manual.generate(ctx);

        assertEquals("manual-secret-credentials",
                ((Map<?, ?>) YamlAssertions.parse(yaml).get("metadata")).get("name"));
    }
}
