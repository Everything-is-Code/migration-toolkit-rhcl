package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated unit tests for individual {@link ResourceGenerator} implementations (#192).
 * No CDI container required — uses plain instantiation and manual wiring.
 */
class IsolatedGeneratorTest {

    // ── GatewayGenerator ────────────────────────────────────────────────────

    @Test
    void gatewayGenerator_minimalContext_producesValidGatewayYaml() {
        GatewayGenerator generator = new GatewayGenerator();

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "my-api";
        service.systemName = "my-api";

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertTrue(generator.applies(ctx), "GatewayGenerator must apply to a standard service");

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        assertEquals("gateway.networking.k8s.io/v1", parsed.get("apiVersion"),
                "must include Gateway API version");
        assertEquals("Gateway", parsed.get("kind"), "must include kind Gateway");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertTrue(metadata.get("name").toString().contains("my-api-gateway"),
                "name must be derived from systemName");
        assertEquals("test-ns", metadata.get("namespace"), "must use the supplied namespace");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("istio", spec.get("gatewayClassName"), "must use istio gateway class");
    }

    @Test
    void gatewayGenerator_outputKey_isGatewayYaml() {
        assertEquals("gateway.yaml", new GatewayGenerator().outputKey());
    }

    // ── RateLimitPolicyGenerator ────────────────────────────────────────────

    @Test
    void rateLimitPolicyGenerator_applies_returnsFalse_whenNoPoliciesAndNoPlans() {
        RateLimitPolicyGenerator generator = new RateLimitPolicyGenerator();

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "no-limits";
        service.systemName = "no-limits";

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertFalse(generator.applies(ctx),
                "applies() must return false when no rate-limit policies or plans exist");
    }

    @Test
    void rateLimitPolicyGenerator_applies_returnsTrue_whenEdgeLimitingEnabled() {
        RateLimitPolicyGenerator generator = new RateLimitPolicyGenerator();

        Policy edgeLimiting = new Policy();
        edgeLimiting.name = "edge_limiting";
        edgeLimiting.enabled = true;

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "with-limits";
        service.systemName = "with-limits";
        service.policies = List.of(edgeLimiting);

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertTrue(generator.applies(ctx),
                "applies() must return true when edge_limiting policy is enabled");
    }

    @Test
    void rateLimitPolicyGenerator_applies_returnsTrue_whenEdgeLimitingNameCaseDiffers() {
        RateLimitPolicyGenerator generator = new RateLimitPolicyGenerator();

        Policy edgeLimiting = new Policy();
        edgeLimiting.name = "Edge_Limiting";
        edgeLimiting.enabled = true;

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "case-limits";
        service.systemName = "case-limits";
        service.policies = List.of(edgeLimiting);

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertTrue(generator.applies(ctx),
                "applies() must match edge_limiting case-insensitively like RateLimitSupport.findEnabled");
    }

    @Test
    void rateLimitPolicyGenerator_applies_returnsFalse_whenEnabledIsNull() {
        RateLimitPolicyGenerator generator = new RateLimitPolicyGenerator();

        Policy edgeLimiting = new Policy();
        edgeLimiting.name = "edge_limiting";
        edgeLimiting.enabled = null;

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "null-enabled";
        service.systemName = "null-enabled";
        service.policies = List.of(edgeLimiting);

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertFalse(generator.applies(ctx),
                "applies() must not throw NPE and must return false when enabled=null (#192)");
    }

    @Test
    void rateLimitPolicyGenerator_applies_returnsTrue_whenPlanHasLimits() {
        RateLimitPolicyGenerator generator = new RateLimitPolicyGenerator();

        ApplicationPlan plan = new ApplicationPlan();
        plan.id = "plan-1";
        plan.name = "Basic";
        plan.limits = List.of(Map.of("period", "minute", "value", 100));

        ApiService service = new ApiService();
        service.id = "1";
        service.name = "plan-limits";
        service.systemName = "plan-limits";
        service.applicationPlans = List.of(plan);

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        assertTrue(generator.applies(ctx),
                "applies() must return true when an application plan has usage limits");
    }
}
