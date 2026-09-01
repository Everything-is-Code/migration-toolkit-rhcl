package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.LimitDefinition;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicyManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for RateLimitSupport typed migration (#262 task 5).
 */
class RateLimitSupportEdgeCaseTest {

    private final RateLimitSupport support = RateLimitSupport.forManual();

    // 5.4 — rate 0 or window 0 → toPositiveInt returns null, limiter is skipped
    @Test
    void generateRateLimitPolicy_zeroRate_skipsLimiter() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.policies.add(ConversionSupportTestFixtures.policy("edge_limiting", true, Map.of(
                "fixed_window_limiters", List.of(
                        Map.of("count", 0, "window", 60)))));

        RateLimitPolicyManifest manifest = support.buildManifest("demo-api", "demo-ns", service);

        assertNull(manifest, "Zero rate limiter should be skipped, resulting in no manifest");
    }

    // 5.5 — multiple limits preserve insertion order (LinkedHashMap)
    @Test
    void buildManifest_multipleLimits_orderIsDeterministic() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.policies.add(ConversionSupportTestFixtures.policy("edge_limiting", true, Map.of(
                "fixed_window_limiters", List.of(
                        Map.of("count", 10, "window", 60, "key", Map.of("name", "alpha")),
                        Map.of("count", 20, "window", 30, "key", Map.of("name", "beta"))))));

        RateLimitPolicyManifest manifest = support.buildManifest("demo-api", "demo-ns", service);

        assertNotNull(manifest);
        assertNotNull(manifest.spec().limits());
        List<String> keys = new java.util.ArrayList<>(manifest.spec().limits().keySet());
        assertEquals(2, keys.size());
        // alpha comes before beta (insertion order)
        assertTrue(keys.get(0).contains("alpha"));
        assertTrue(keys.get(1).contains("beta"));
    }

    // 5.5 — Rate(0, window) serializes as 0, not omitted
    @Test
    void rateWithZeroLimit_serializesAsZero() {
        com.redhat.migrationtoolkit.rhcl.model.kuadrant.Rate rate =
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.Rate(0, "60s");
        LimitDefinition def = new LimitDefinition(List.of(rate));
        ManifestSerializer serializer = new ManifestSerializer();
        String yaml = serializer.toYaml(new RateLimitPolicyManifest(
                "kuadrant.io/v1", "RateLimitPolicy",
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta(
                        "test-ratelimit", "ns", Map.of(), null),
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicySpec(
                        new com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef(
                                "gateway.networking.k8s.io", "HTTPRoute", "test-route"),
                        Map.of("test-limit", def))));

        assertTrue(yaml.contains("limit: 0"), "Zero limit should serialize as 0, not be omitted");
    }

    // 5.4 — negative rate values are skipped (toPositiveInt requires > 0)
    @Test
    void buildManifest_negativeRate_skipsLimiter() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.policies.add(ConversionSupportTestFixtures.policy("edge_limiting", true, Map.of(
                "fixed_window_limiters", List.of(
                        Map.of("count", -5, "window", 60)))));

        RateLimitPolicyManifest manifest = support.buildManifest("demo-api", "demo-ns", service);

        assertNull(manifest);
    }

    // plan ceiling — max across multiple plans
    @Test
    void resolvePlanCeiling_multipleMinutePlans_returnsMax() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        ApplicationPlan plan1 = new ApplicationPlan();
        plan1.limits = List.of(Map.of("period", "minute", "value", 10));
        ApplicationPlan plan2 = new ApplicationPlan();
        plan2.limits = List.of(Map.of("period", "minute", "value", 50));
        service.applicationPlans = List.of(plan1, plan2);

        RateLimitSupport.PlanCeiling ceiling = support.resolvePlanCeiling(service);

        assertNotNull(ceiling);
        assertEquals(50, ceiling.limit(), "Should use the max limit across all plans");
        assertEquals("60s", ceiling.window());
    }
}
