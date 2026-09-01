package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("configmap.yaml", generator.outputKey());
        assertEquals("v1", parsed.get("apiVersion"));
        assertEquals("ConfigMap", parsed.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("config-api-config", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) parsed.get("data");
        assertTrue(data.get("backend-url").contains("https://backend.example.com"));
        assertEquals("config-api", data.get("service-name"));
        assertEquals("42", data.get("original-3scale-service-id"));
    }

    @Test
    void generate_withNoBackends_producesEmptyBackendUrl() {
        ApiService service = GeneratorTestSupport.basicService("Empty API", "empty-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) parsed.get("data");

        assertEquals("", data.get("backend-url"));
        assertNotNull(parsed.get("data"));
    }

    @Test
    void generate_withoutMigratedFromLabel_omitsLabel() {
        ApiService service = GeneratorTestSupport.basicService("Empty API", "empty-api");
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
