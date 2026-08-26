package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RateLimitPolicyGeneratorTest {

    @Inject
    RateLimitPolicyGenerator generator;

    @Test
    void applies_returnsFalse_whenNoRateLimitSources() {
        ApiService service = GeneratorTestSupport.basicService("No Limits", "no-limits");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void applies_returnsTrue_whenEdgeLimitingEnabled() {
        Policy edgeLimiting = GeneratorTestSupport.enabledPolicy("edge_limiting");
        ApiService service = GeneratorTestSupport.basicService("Limited API", "limited-api");
        service.policies = List.of(edgeLimiting);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsTrue_whenPlanHasLimits() {
        ApplicationPlan plan = new ApplicationPlan();
        plan.limits = List.of(Map.of("period", "minute", "value", 100));
        ApiService service = GeneratorTestSupport.basicService("Plan API", "plan-api");
        service.applicationPlans = List.of(plan);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesRateLimitPolicyYaml() {
        Policy edgeLimiting = GeneratorTestSupport.enabledPolicy("edge_limiting");
        Map<String, Object> limiter = new HashMap<>();
        limiter.put("count", 10);
        limiter.put("window", 60);
        Map<String, Object> key = new HashMap<>();
        key.put("name", "service");
        key.put("scope", "service");
        limiter.put("key", key);
        edgeLimiting.configuration = Map.of("fixed_window_limiters", List.of(limiter));

        ApiService service = GeneratorTestSupport.basicService("Limited API", "limited-api");
        service.policies = List.of(edgeLimiting);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("ratelimitpolicy.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(yaml.contains("kind: RateLimitPolicy"));
        assertTrue(yaml.contains("name: limited-api-ratelimit"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
    }
}
