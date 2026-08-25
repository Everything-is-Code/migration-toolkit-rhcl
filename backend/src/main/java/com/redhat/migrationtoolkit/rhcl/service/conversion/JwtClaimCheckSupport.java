package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** JWT claim check parsing shared by AuthPolicy contributor and README notes. */
public final class JwtClaimCheckSupport {

    public record JwtClaimPattern(String selector, String operator, String value) {}

    public record JwtClaimParseResult(List<JwtClaimPattern> patterns, List<String> gapNotes) {}

    private JwtClaimCheckSupport() {
    }

  @SuppressWarnings("unchecked")
    public static JwtClaimParseResult parseRules(Policy policy) {
        List<JwtClaimPattern> patterns = new ArrayList<>();
        List<String> gapNotes = new ArrayList<>();
        if (policy == null || policy.configuration == null) {
            return new JwtClaimParseResult(patterns, gapNotes);
        }
        Object rulesRaw = policy.configuration.get("rules");
        if (!(rulesRaw instanceof List<?> rules)) {
            return new JwtClaimParseResult(patterns, gapNotes);
        }
        if (Boolean.TRUE.equals(policy.configuration.get("enable_extended_context"))) {
            gapNotes.add("enable_extended_context is not converted — claim checks use plain JWT identity only");
        }
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) ruleMap;
            String combineOp = String.valueOf(rule.getOrDefault("combine_op", "and")).trim().toLowerCase(Locale.ROOT);
            if ("or".equals(combineOp)) {
                gapNotes.add("combine_op=or is not supported — Authorino authorization rules are AND across patterns; OR rule skipped");
                continue;
            }
            String resourceType = String.valueOf(rule.getOrDefault("resource_type", "plain")).trim().toLowerCase(Locale.ROOT);
            if ("liquid".equals(resourceType)) {
                gapNotes.add("resource_type=liquid is not converted — path gating ignored; claim patterns may still apply globally");
            }
            if (!isCatchAllResource(rule)) {
                gapNotes.add("path/method-gated jwt_claim_check rules are applied globally in AuthPolicy (no Authorino when/path scoping in P1)");
            }
            Object opsRaw = rule.get("operations");
            if (!(opsRaw instanceof List<?> ops)) {
                continue;
            }
            for (Object opObj : ops) {
                if (!(opObj instanceof Map<?, ?> opMap)) {
                    continue;
                }
                Map<String, Object> op = (Map<String, Object>) opMap;
                String claimType = String.valueOf(op.getOrDefault("jwt_claim_type", "plain")).trim().toLowerCase(Locale.ROOT);
                String valueType = String.valueOf(op.getOrDefault("value_type", "plain")).trim().toLowerCase(Locale.ROOT);
                if ("liquid".equals(claimType) || "liquid".equals(valueType)) {
                    gapNotes.add("liquid jwt_claim/value is not converted — operation skipped");
                    continue;
                }
                Object claimRaw = op.get("jwt_claim");
                if (claimRaw == null || claimRaw.toString().isBlank()) {
                    continue;
                }
                String claim = claimRaw.toString().trim();
                String threeScaleOp = String.valueOf(op.getOrDefault("op", "")).trim();
                String authorinoOp = mapOp(threeScaleOp);
                if (authorinoOp == null) {
                    gapNotes.add("unsupported jwt_claim_check op '" + threeScaleOp + "' — skipped");
                    continue;
                }
                Object valueRaw = op.get("value");
                String value = valueRaw != null ? valueRaw.toString() : "";
                patterns.add(new JwtClaimPattern("auth.identity." + claim, authorinoOp, value));
            }
        }
        return new JwtClaimParseResult(patterns, gapNotes);
    }

    public static String buildNamedRule(List<JwtClaimPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("      jwt-claim-check:\n");
        sb.append("        patternMatching:\n");
        sb.append("          patterns:\n");
        for (JwtClaimPattern pattern : patterns) {
            sb.append("            - selector: ").append(pattern.selector()).append('\n');
            sb.append("              operator: ").append(pattern.operator()).append('\n');
            sb.append("              value: ").append(HttpRouteSupport.yamlDoubleQuoted(pattern.value())).append('\n');
        }
        return sb.toString();
    }

    public static String buildReadmeNotes(ApiService service, PolicyFinder policyFinder) {
        Policy claimCheck = policyFinder.findEnabled(service, "jwt_claim_check");
        if (claimCheck == null) {
            return "";
        }
        JwtClaimParseResult parsed = parseRules(claimCheck);
        if (parsed.gapNotes().isEmpty() && parsed.patterns().isEmpty()) {
            return "";
        }
        if (parsed.gapNotes().isEmpty()) {
            return """

## JWT Claim Check

`policy.yaml` includes AuthPolicy `authorization.jwt-claim-check` patternMatching rules
mapped from 3scale `jwt_claim_check` (`==`→`eq`, `!=`→`neq`, `matches`→`matches` on `auth.identity.*`).
""";
        }
        StringBuilder bullets = new StringBuilder();
        for (String note : parsed.gapNotes().stream().distinct().toList()) {
            bullets.append("- ").append(note).append('\n');
        }
        return """

## WARNING: JWT Claim Check conversion gaps

3scale `jwt_claim_check` was partially converted to AuthPolicy `patternMatching`.
Review the following limitations before apply:

%s
Liquid claim/value templates, `combine_op=or`, and path/method-gated deny semantics are not fully
reproduced — verify authorization behavior against the original APIcast policy.
""".formatted(bullets);
    }

    private static String mapOp(String threeScaleOp) {
        return switch (threeScaleOp) {
            case "==" -> "eq";
            case "!=" -> "neq";
            case "matches" -> "matches";
            default -> null;
        };
    }

    private static boolean isCatchAllResource(Map<String, Object> rule) {
        String resource = rule.get("resource") != null ? rule.get("resource").toString().trim() : "";
        boolean resourceOk = resource.isEmpty() || "/".equals(resource) || ".*".equals(resource);
        List<String> methods = HttpRouteSupport.toStringList(rule.get("methods"));
        boolean methodsOk = methods.isEmpty()
                || methods.stream().anyMatch(m -> "ANY".equalsIgnoreCase(m));
        return resourceOk && methodsOk;
    }
}
