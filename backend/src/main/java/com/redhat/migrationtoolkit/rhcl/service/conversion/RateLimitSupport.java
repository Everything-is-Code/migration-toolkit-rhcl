package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.LimitDefinition;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.Rate;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rate-limit policy construction and plan-ceiling resolution shared by generator and README notes.
 * Produces typed {@link RateLimitPolicyManifest} records; serialization is the generator's concern.
 * WARNING comments previously emitted inline are now carried only via README notes (Jackson cannot
 * serialize YAML comments).
 */
@ApplicationScoped
public class RateLimitSupport {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ManifestSerializer manifestSerializer;

    /** Manual wiring when {@link PolicyFinder} is not injected. */
    public static RateLimitSupport forManual() {
        RateLimitSupport support = new RateLimitSupport();
        support.policyFinder = new com.redhat.migrationtoolkit.rhcl.service.PolicyFinder();
        return support;
    }

    public record PlanCeiling(int limit, String window) {}

    public PlanCeiling resolvePlanCeiling(ApiService service) {
        if (service.applicationPlans == null || service.applicationPlans.isEmpty()) {
            return null;
        }
        Integer maxMinute = null;
        Integer maxHour = null;
        for (ApplicationPlan plan : service.applicationPlans) {
            if (plan == null || plan.limits == null) {
                continue;
            }
            for (Map<String, Object> limit : plan.limits) {
                if (limit == null) {
                    continue;
                }
                Object periodObj = limit.get("period");
                Integer value = toPositiveInt(limit.get("value"));
                if (periodObj == null || value == null) {
                    continue;
                }
                String period = String.valueOf(periodObj).toLowerCase();
                if ("minute".equals(period)) {
                    maxMinute = maxMinute == null ? value : Math.max(maxMinute, value);
                } else if ("hour".equals(period)) {
                    maxHour = maxHour == null ? value : Math.max(maxHour, value);
                }
            }
        }
        if (maxMinute != null) {
            return new PlanCeiling(maxMinute, "60s");
        }
        if (maxHour != null) {
            return new PlanCeiling(maxHour, "3600s");
        }
        return null;
    }

    /**
     * Build a typed {@link RateLimitPolicyManifest} for the given service, or {@code null} when no
     * rate-limit sources are present.
     */
    public RateLimitPolicyManifest buildManifest(String name, String namespace, ApiService service) {
        return buildManifest(name, namespace, service, true);
    }

    public RateLimitPolicyManifest buildManifest(
            String name, String namespace, ApiService service, boolean includeMigratedFromLabel) {
        Map<String, LimitDefinition> limits = new LinkedHashMap<>();

        Policy edge = policyFinder.findEnabled(service, "edge_limiting");
        if (edge != null && edge.configuration != null) {
            appendEdgeLimitingRates(limits, edge.configuration);
        }

        PlanCeiling ceiling = resolvePlanCeiling(service);
        if (ceiling != null) {
            limits.put("global", new LimitDefinition(List.of(new Rate(ceiling.limit, ceiling.window))));
        }

        if (limits.isEmpty()) {
            return null;
        }

        ManifestMeta meta = KuadrantManifestSupport.meta(
                name + "-ratelimit", namespace, name, includeMigratedFromLabel);

        TargetRef targetRef = new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route");
        RateLimitPolicySpec spec = new RateLimitPolicySpec(targetRef, limits);

        return new RateLimitPolicyManifest("kuadrant.io/v1", "RateLimitPolicy", meta, spec);
    }

    /**
     * Convenience method: serialize to YAML via the provided serializer.
     * Returns {@code null} when no limits are present.
     */
    public String generateRateLimitPolicy(String name, String namespace, ApiService service) {
        RateLimitPolicyManifest manifest = buildManifest(name, namespace, service);
        if (manifest == null) {
            return null;
        }
        return KuadrantManifestSupport.resolveSerializer(manifestSerializer).toYaml(manifest);
    }

    @SuppressWarnings("unchecked")
    private void appendEdgeLimitingRates(Map<String, LimitDefinition> limits, Map<String, Object> cfg) {
        int idx = 1;
        Object fixed = cfg.get("fixed_window_limiters");
        if (fixed instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer count = toPositiveInt(limiter.get("count"));
                Integer window = toPositiveInt(limiter.get("window"));
                if (count == null || window == null) {
                    continue;
                }
                String limitName = edgeLimiterName(limiter, "edge_fixed_window", idx++);
                limits.put(limitName, new LimitDefinition(List.of(new Rate(count, window + "s"))));
            }
        }

        Object leaky = cfg.get("leaky_bucket_limiters");
        if (leaky instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer rate = toPositiveInt(limiter.get("rate"));
                if (rate == null) {
                    continue;
                }
                String limitName = edgeLimiterName(limiter, "edge_leaky_bucket", idx++);
                limits.put(limitName, new LimitDefinition(List.of(new Rate(rate, "1s"))));
            }
        }

        Object conn = cfg.get("connection_limiters");
        if (conn instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer connLimit = toPositiveInt(limiter.get("conn"));
                if (connLimit == null) {
                    continue;
                }
                String limitName = edgeLimiterName(limiter, "edge_conn", idx++);
                limits.put(limitName, new LimitDefinition(List.of(new Rate(connLimit, "1s"))));
            }
        }
    }

    private static String edgeLimiterName(Map<String, Object> limiter, String prefix, int idx) {
        Object keyObj = limiter.get("key");
        if (keyObj instanceof Map<?, ?> keyMap) {
            Object name = keyMap.get("name");
            if (name != null) {
                String sanitized = String.valueOf(name)
                        .replaceAll("[^a-zA-Z0-9_-]", "_")
                        .replaceAll("^_+|_+$", "");
                if (!sanitized.isBlank() && !sanitized.contains("{{")) {
                    return sanitized + "_" + idx;
                }
            }
        }
        return prefix + "_" + idx;
    }

    private static Integer toPositiveInt(Object value) {
        if (value instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        if (value != null) {
            try {
                int v = Integer.parseInt(String.valueOf(value).trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
