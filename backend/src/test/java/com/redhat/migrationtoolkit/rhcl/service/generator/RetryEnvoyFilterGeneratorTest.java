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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("envoyfilter-retry.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("EnvoyFilter", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("retry-api-retry", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("retry", annotations.get("3scale-migration/source"));
    }

    // ── Edge case 4.7 ────────────────────────────────────────────────────────

    @Test
    void generate_structuralParse_configPatchesHaveNoNullNoise() {
        Policy retry = GeneratorTestSupport.policyWithConfig("retry", Map.of("retries", 2));
        ApiService service = GeneratorTestSupport.basicService("Retry API", "retry-api");
        service.policies = List.of(retry);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertNotNull(spec, "spec must be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> workloadSelector = (Map<String, Object>) spec.get("workloadSelector");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) workloadSelector.get("labels");
        assertEquals("retry-api-gateway", labels.get("gateway.networking.k8s.io/gateway-name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        assertNotNull(configPatches, "configPatches must be present");
        assertEquals(1, configPatches.size());

        Map<String, Object> patch = configPatches.get(0);
        assertEquals("HTTP_ROUTE", patch.get("applyTo"));
        assertNotNull(patch.get("match"), "match must not be null");
        assertNotNull(patch.get("patch"), "patch value must not be null");

        @SuppressWarnings("unchecked")
        Map<String, Object> patchValue = (Map<String, Object>) patch.get("patch");
        assertEquals("MERGE", patchValue.get("operation"));
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) patchValue.get("value");
        @SuppressWarnings("unchecked")
        Map<String, Object> route = (Map<String, Object>) value.get("route");
        @SuppressWarnings("unchecked")
        Map<String, Object> retryPolicy = (Map<String, Object>) route.get("retry_policy");
        assertEquals(2, retryPolicy.get("num_retries"));
    }
}
