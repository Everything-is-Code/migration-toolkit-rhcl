package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContentLimitsEnvoyFilterGeneratorTest {

    @Inject
    ContentLimitsEnvoyFilterGenerator generator;

    @Test
    void applies_returnsTrue_whenRequestContentLimitConfigured() {
        Policy contentLimits = GeneratorTestSupport.policyWithConfig("content_limits",
                Map.of("request", 1024));
        ApiService service = GeneratorTestSupport.basicService("Limits API", "limits-api");
        service.policies = List.of(contentLimits);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenContentLimitsPolicyAbsent() {
        ApiService service = GeneratorTestSupport.basicService("Plain API", "plain-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesEnvoyFilterWithMaxRequestBytes() {
        Policy contentLimits = GeneratorTestSupport.policyWithConfig("content_limits",
                Map.of("request", 2048));
        ApiService service = GeneratorTestSupport.basicService("Limits API", "limits-api");
        service.policies = List.of(contentLimits);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("envoyfilter-content-limits.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("EnvoyFilter", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("limits-api-content-limits", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("content_limits", annotations.get("3scale-migration/source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        assertNotNull(configPatches);
        assertEquals(1, configPatches.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> patchValue = (Map<String, Object>) configPatches.get(0).get("patch");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) patchValue.get("value");
        @SuppressWarnings("unchecked")
        Map<String, Object> typedConfig = (Map<String, Object>) value.get("typed_config");
        assertEquals(2048, typedConfig.get("max_request_bytes"));
    }

    // ── Edge case 4.5 ────────────────────────────────────────────────────────

    @Test
    void applies_returnsFalse_whenRequestLimitIsZero() {
        Policy contentLimits = GeneratorTestSupport.policyWithConfig("content_limits",
                Map.of("request", 0));
        ApiService service = GeneratorTestSupport.basicService("Zero API", "zero-api");
        service.policies = List.of(contentLimits);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when request limit is zero");
    }

    @Test
    void applies_returnsFalse_whenRequestLimitIsNegative() {
        Policy contentLimits = GeneratorTestSupport.policyWithConfig("content_limits",
                Map.of("request", -1));
        ApiService service = GeneratorTestSupport.basicService("Neg API", "neg-api");
        service.policies = List.of(contentLimits);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when request limit is negative");
    }
}
