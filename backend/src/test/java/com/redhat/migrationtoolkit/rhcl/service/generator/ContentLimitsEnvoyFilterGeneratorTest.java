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

        assertNotNull(yaml);
        assertEquals("envoyfilter-content-limits.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: EnvoyFilter"));
        assertTrue(yaml.contains("name: limits-api-content-limits"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: content_limits"));
        assertTrue(yaml.contains("max_request_bytes: 2048"));
        assertTrue(yaml.contains("gateway.networking.k8s.io/gateway-name: limits-api-gateway"));
    }
}
