package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
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

        assertNotNull(yaml);
        assertEquals("telemetry.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: telemetry.istio.io/v1"));
        assertTrue(yaml.contains("kind: Telemetry"));
        assertTrue(yaml.contains("name: log-api-logging"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: logging"));
        assertTrue(yaml.contains("accessLogging:"));
        assertTrue(yaml.contains("gateway.networking.k8s.io/gateway-name: log-api-gateway"));
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

        assertTrue(yaml.contains("app: log-api"));
        assertFalse(yaml.contains("gateway.networking.k8s.io/gateway-name"));
    }
}
