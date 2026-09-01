package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("destinationrule.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("DestinationRule", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("dr-api-backend-tls", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("secure.example.com", spec.get("host"));
        assertNotNull(spec.get("trafficPolicy"), "trafficPolicy must be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> trafficPolicy = (Map<String, Object>) spec.get("trafficPolicy");
        assertNotNull(trafficPolicy.get("tls"), "tls block must be present");
    }

    // ── Edge case 4.2 ────────────────────────────────────────────────────────

    @Test
    void generate_httpExternalBackend_hasTlsModeDisable() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "plain-dr", "http://api.plain.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> trafficPolicy = (Map<String, Object>) spec.get("trafficPolicy");
        @SuppressWarnings("unchecked")
        Map<String, Object> tls = (Map<String, Object>) trafficPolicy.get("tls");

        assertNotNull(tls, "tls block must not be null for http (non-tls) backend");
        assertEquals("DISABLE", tls.get("mode"),
                "non-TLS external backend must produce tls.mode=DISABLE");
    }

    @Test
    void generate_httpsExternalBackend_usesSimpleTlsMode() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "dr-api", "https://secure.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> trafficPolicy = (Map<String, Object>) ((Map<String, Object>) parsed.get("spec")).get("trafficPolicy");
        @SuppressWarnings("unchecked")
        Map<String, Object> tls = (Map<String, Object>) trafficPolicy.get("tls");

        assertEquals("SIMPLE", tls.get("mode"));
        assertEquals("secure.example.com", tls.get("sni"));
    }
}
