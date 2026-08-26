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
class RetryEnvoyFilterGeneratorTest {

    @Inject
    RetryEnvoyFilterGenerator generator;

    @Test
    void applies_returnsTrue_whenRetryPolicyPresentAndHttpRouteRetryUnsupported() {
        Policy retry = GeneratorTestSupport.policyWithConfig("retry", Map.of("retries", 3));
        ApiService service = GeneratorTestSupport.basicService("Retry API", "retry-api");
        service.policies = List.of(retry);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenRetriesSupportedOnCluster() {
        Policy retry = GeneratorTestSupport.policyWithConfig("retry", Map.of("retries", 3));
        ApiService service = GeneratorTestSupport.basicService("Retry API", "retry-api");
        service.policies = List.of(retry);
        ConversionOptions options = new ConversionOptions();
        options.retriesSupported = true;
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesEnvoyFilterWithRetryPolicy() {
        Policy retry = GeneratorTestSupport.policyWithConfig("retry", Map.of("retries", 2));
        ApiService service = GeneratorTestSupport.basicService("Retry API", "retry-api");
        service.policies = List.of(retry);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("envoyfilter-retry.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: EnvoyFilter"));
        assertTrue(yaml.contains("name: retry-api-retry"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: retry"));
        assertTrue(yaml.contains("retry_policy:"));
        assertTrue(yaml.contains("num_retries: 2"));
        assertTrue(yaml.contains("gateway.networking.k8s.io/gateway-name: retry-api-gateway"));
    }
}
