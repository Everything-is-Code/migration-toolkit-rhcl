package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
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
class LoggingEnvoyFilterGeneratorTest {

    @Inject
    LoggingEnvoyFilterGenerator generator;

    @Test
    void applies_returnsTrue_whenLoggingPolicyHasJsonConfig() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "json_object_config", List.of(Map.of("key", "method", "value", "%REQ(:METHOD)%"))));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenLoggingPolicyAbsent() {
        ApiService service = GeneratorTestSupport.basicService("Plain API", "plain-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesEnvoyFilterWithAccessLogFormat() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "json_object_config", List.of(Map.of("key", "method", "value", "%REQ(:METHOD)%"))));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("envoyfilter-logging.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("EnvoyFilter", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("log-api-logging-format", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("logging", annotations.get("3scale-migration/source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> workloadSelector = (Map<String, Object>) spec.get("workloadSelector");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) workloadSelector.get("labels");
        assertEquals("log-api-gateway", labels.get("gateway.networking.k8s.io/gateway-name"),
                "default logging target is gateway");

        assertNotNull(spec.get("configPatches"), "configPatches must be present");
    }

    @Test
    void generate_workloadLoggingTarget_usesSidecarInboundContext() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "json_object_config", List.of(Map.of("key", "method", "value", "%REQ(:METHOD)%"))));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionOptions options = new ConversionOptions();
        options.loggingTarget = "workload";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) ((Map<String, Object>) spec.get("workloadSelector")).get("labels");
        assertEquals("log-api", labels.get("app"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        @SuppressWarnings("unchecked")
        Map<String, Object> match = (Map<String, Object>) configPatches.get(0).get("match");
        assertEquals("SIDECAR_INBOUND", match.get("context"));
    }

    // ── Edge case 4.4 ────────────────────────────────────────────────────────

    @Test
    void applies_returnsFalse_whenJsonObjectConfigIsEmptyList() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "json_object_config", List.of()));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when json_object_config is an empty list");
    }

    @Test
    void applies_returnsFalse_whenJsonObjectConfigKeyMissing() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of());
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when json_object_config key is absent");
    }
}
