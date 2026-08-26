package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitSupportTest {

    private final RateLimitSupport support = RateLimitSupport.forManual();

    @Test
    void resolvePlanCeiling_minuteTakesPrecedenceOverHour() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        ApplicationPlan plan = new ApplicationPlan();
        plan.limits = List.of(
                Map.of("period", "hour", "value", 1000),
                Map.of("period", "minute", "value", 60));
        service.applicationPlans = List.of(plan);

        RateLimitSupport.PlanCeiling ceiling = support.resolvePlanCeiling(service);

        assertNotNull(ceiling);
        assertEquals(60, ceiling.limit());
        assertEquals("60s", ceiling.window());
    }

    @Test
    void resolvePlanCeiling_hourOnly_returnsHourWindow() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        ApplicationPlan plan = new ApplicationPlan();
        plan.limits = List.of(Map.of("period", "hour", "value", 500));
        service.applicationPlans = List.of(plan);

        RateLimitSupport.PlanCeiling ceiling = support.resolvePlanCeiling(service);

        assertNotNull(ceiling);
        assertEquals(500, ceiling.limit());
        assertEquals("3600s", ceiling.window());
    }

    @Test
    void resolvePlanCeiling_noPlans_returnsNull() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        assertNull(support.resolvePlanCeiling(service));
    }

    @Test
    void generateRateLimitPolicy_edgeLimitingFixedWindow_emitsRates() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.policies.add(ConversionSupportTestFixtures.policy("edge_limiting", true, Map.of(
                "fixed_window_limiters", List.of(
                        Map.of("count", 100, "window", 60, "key", Map.of("name", "user"))))));

        String yaml = support.generateRateLimitPolicy("demo-api", "demo-ns", service);

        assertNotNull(yaml);
        assertTrue(yaml.contains("kind: RateLimitPolicy"));
        assertTrue(yaml.contains("limit: 100"));
        assertTrue(yaml.contains("window: 60s"));
        assertTrue(yaml.contains("name: demo-api-ratelimit"));
    }

    @Test
    void generateRateLimitPolicy_richFixture_emitsLeakyBucketAndConnectionLimiters() {
        ApiService service = ConversionSupportTestFixtures.richService();
        String yaml = support.generateRateLimitPolicy("demo-api", "demo-ns", service);

        assertNotNull(yaml);
        assertTrue(yaml.contains("edge_leaky_bucket"));
        assertTrue(yaml.contains("leaky_bucket_limiters approximated"));
        assertTrue(yaml.contains("edge_connection"));
        assertTrue(yaml.contains("connection_limiters mapped"));
    }
}
