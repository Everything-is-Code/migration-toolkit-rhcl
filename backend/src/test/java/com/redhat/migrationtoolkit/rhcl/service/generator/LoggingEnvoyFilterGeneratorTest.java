package com.redhat.migrationtoolkit.rhcl.service.generator;

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

        assertNotNull(yaml);
        assertEquals("envoyfilter-logging.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: EnvoyFilter"));
        assertTrue(yaml.contains("name: log-api-logging-format"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: logging"));
        assertTrue(yaml.contains("json_format:"));
        assertTrue(yaml.contains("gateway.networking.k8s.io/gateway-name: log-api-gateway"));
    }
}
