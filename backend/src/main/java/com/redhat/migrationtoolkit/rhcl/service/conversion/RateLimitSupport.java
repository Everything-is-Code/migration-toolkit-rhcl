package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rate-limit policy YAML and plan-ceiling resolution shared by generator and README notes.
 */
@ApplicationScoped
public class RateLimitSupport {

    @Inject
    PolicyFinder policyFinder;

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

    public String generateRateLimitPolicy(String name, String namespace, ApiService service) {
        Map<String, String> limitBlocks = new LinkedHashMap<>();

        Policy edge = policyFinder.findEnabled(service, "edge_limiting");
        if (edge != null && edge.configuration != null) {
            appendEdgeLimitingRates(limitBlocks, edge.configuration);
        }

        PlanCeiling ceiling = resolvePlanCeiling(service);
        if (ceiling != null) {
            limitBlocks.put("global", """
      # WARNING: plan ceiling is the max limit across all application plans (not per-plan)
      rates:
        - limit: %d
          window: %s
""".formatted(ceiling.limit, ceiling.window));
        }

        if (limitBlocks.isEmpty()) {
            return null;
        }

        StringBuilder limitsYaml = new StringBuilder();
        for (Map.Entry<String, String> entry : limitBlocks.entrySet()) {
            limitsYaml.append("    ").append(entry.getKey()).append(":\n");
            for (String line : entry.getValue().split("\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                limitsYaml.append(line).append('\n');
            }
        }

        return """
apiVersion: kuadrant.io/v1
kind: RateLimitPolicy
metadata:
  name: %s-ratelimit
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  limits:
%s""".formatted(name, namespace, name, name, limitsYaml);
    }

    @SuppressWarnings("unchecked")
    private void appendEdgeLimitingRates(Map<String, String> limitBlocks, Map<String, Object> cfg) {
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
                limitBlocks.put(limitName, """
      rates:
        - limit: %d
          window: %ds
""".formatted(count, window));
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
                limitBlocks.put(limitName, """
      # WARNING: 3scale leaky_bucket_limiters approximated as fixed window (window: 1s); not true leaky-bucket semantics
      rates:
        - limit: %d
          window: 1s
""".formatted(rate));
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
                limitBlocks.put(limitName, """
      # WARNING: 3scale connection_limiters mapped to a per-second rate ceiling; concurrent-connection semantics are not preserved
      rates:
        - limit: %d
          window: 1s
""".formatted(connLimit));
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
