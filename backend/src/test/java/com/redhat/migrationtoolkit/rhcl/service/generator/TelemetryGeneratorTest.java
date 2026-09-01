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
class TelemetryGeneratorTest {

    @Inject
    TelemetryGenerator generator;

    @Test
    void applies_returnsTrue_whenLoggingPolicyPresent() {
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(GeneratorTestSupport.enabledPolicy("logging"));
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
    void generate_producesTelemetryYaml() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "enable_json_logs", true,
                "enable_access_logs", true));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("telemetry.yaml", generator.outputKey());
        assertEquals("telemetry.istio.io/v1", parsed.get("apiVersion"));
        assertEquals("Telemetry", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("log-api-logging", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("logging", annotations.get("3scale-migration/source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> selector = (Map<String, Object>) spec.get("selector");
        @SuppressWarnings("unchecked")
        Map<String, Object> matchLabels = (Map<String, Object>) selector.get("matchLabels");
        assertEquals("log-api-gateway", matchLabels.get("gateway.networking.k8s.io/gateway-name"),
                "default logging target must be gateway");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accessLogging = (List<Map<String, Object>>) spec.get("accessLogging");
        assertNotNull(accessLogging);
        assertFalse(accessLogging.isEmpty());
    }

    @Test
    void generate_workloadLoggingTarget_usesAppSelector() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "enable_access_logs", true));
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
        Map<String, Object> selector = (Map<String, Object>) spec.get("selector");
        @SuppressWarnings("unchecked")
        Map<String, Object> matchLabels = (Map<String, Object>) selector.get("matchLabels");
        assertEquals("log-api", matchLabels.get("app"),
                "workload logging target must use app label");
        assertFalse(matchLabels.containsKey("gateway.networking.k8s.io/gateway-name"),
                "workload logging target must not include gateway label");
    }

    @Test
    void generate_accessLogsDisabled_stillProducesTelemetry() {
        Policy logging = GeneratorTestSupport.policyWithConfig("logging", Map.of(
                "enable_access_logs", false,
                "enable_json_logs", false));
        ApiService service = GeneratorTestSupport.basicService("Log API", "log-api");
        service.policies = List.of(logging);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) ((Map<String, Object>) parsed.get("metadata")).get("annotations");
        assertEquals("false", annotations.get("3scale-migration/enable-json"));
        assertEquals("false", annotations.get("3scale-migration/enable-access"));
    }
}
