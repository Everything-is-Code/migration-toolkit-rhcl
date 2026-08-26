package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsing and README helpers for 3scale {@code routing} → HTTPRoute conversion (#150).
 */
public final class RoutingSupport {

    public enum MatchKind {
        HEADER,
        QUERY_ARG,
        PATH,
        JWT_CLAIM,
        UNSUPPORTED
    }

    public record MatchOp(MatchKind kind, String name, String op, String value) {
        public boolean convertible() {
            if (!RoutingSupport.isEqualityOp(op) || value == null) {
                return false;
            }
            return switch (kind) {
                case HEADER, QUERY_ARG -> name != null && !name.isBlank();
                case PATH -> true;
                default -> false;
            };
        }
    }

    public record RoutingRule(String url, String combineOp, List<MatchOp> operations, boolean hasJwtClaim) {
        public List<MatchOp> convertibleOps() {
            return operations.stream().filter(MatchOp::convertible).toList();
        }
    }

    private RoutingSupport() {
    }

    public static boolean isEqualityOp(String op) {
        return op != null && "==".equals(op.trim());
    }

    public static boolean hasJwtClaimOperations(Policy routing) {
        return parseRules(routing).stream().anyMatch(RoutingRule::hasJwtClaim);
    }

    public static List<RoutingRule> parseRules(Policy routing) {
        List<RoutingRule> out = new ArrayList<>();
        if (routing == null || routing.configuration == null) {
            return out;
        }
        Object rulesRaw = routing.configuration.get("rules");
        if (!(rulesRaw instanceof List<?> rules)) {
            return out;
        }
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) ruleMap;
            String url = stringOrNull(rule.get("url"));
            Object conditionRaw = rule.get("condition");
            String combineOp = "and";
            List<MatchOp> ops = new ArrayList<>();
            boolean hasJwt = false;
            if (conditionRaw instanceof Map<?, ?> conditionMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) conditionMap;
                Object combineRaw = condition.get("combine_op");
                if (combineRaw != null && !combineRaw.toString().isBlank()) {
                    combineOp = combineRaw.toString().trim().toLowerCase(Locale.ROOT);
                }
                Object opsRaw = condition.get("operations");
                if (opsRaw instanceof List<?> opList) {
                    for (Object opObj : opList) {
                        if (!(opObj instanceof Map<?, ?> opMap)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> op = (Map<String, Object>) opMap;
                        MatchOp parsed = parseOp(op);
                        if (parsed.kind() == MatchKind.JWT_CLAIM) {
                            hasJwt = true;
                        }
                        ops.add(parsed);
                    }
                }
            }
            if (!"or".equals(combineOp)) {
                combineOp = "and";
            }
            out.add(new RoutingRule(url, combineOp, ops, hasJwt));
        }
        return out;
    }

    static MatchOp parseOp(Map<String, Object> op) {
        String match = stringOrNull(op.get("match"));
        String threeScaleOp = op.get("op") != null ? op.get("op").toString().trim() : "";
        String value = op.get("value") != null ? op.get("value").toString() : null;
        if (match == null) {
            return new MatchOp(MatchKind.UNSUPPORTED, null, threeScaleOp, value);
        }
        return switch (match.toLowerCase(Locale.ROOT)) {
            case "header" -> new MatchOp(MatchKind.HEADER, stringOrNull(op.get("header_name")),
                    threeScaleOp, value);
            case "query_arg" -> new MatchOp(MatchKind.QUERY_ARG, stringOrNull(op.get("query_arg_name")),
                    threeScaleOp, value);
            case "path" -> new MatchOp(MatchKind.PATH, null, threeScaleOp, value);
            case "jwt_claim" -> new MatchOp(MatchKind.JWT_CLAIM, stringOrNull(op.get("jwt_claim_name")),
                    threeScaleOp, value);
            default -> new MatchOp(MatchKind.UNSUPPORTED, null, threeScaleOp, value);
        };
    }

    /**
     * Prefer a product backend whose host matches the override URL; otherwise ephemeral
     * {@link BackendResolver#resolveOne} without mutating {@code ctx.resolvedBackends}.
     */
    public static ResolvedBackend resolveBackendForUrl(ConversionContext ctx, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String overrideHost = UrlUtils.extractHostname(url);
        if (overrideHost != null && ctx.resolvedBackends != null) {
            for (ResolvedBackend backend : ctx.resolvedBackends) {
                if (hostMatches(backend, overrideHost)) {
                    return backend;
                }
            }
        }
        return new BackendResolver().resolveOne(ctx.serviceKebabName, url.trim(), null, "/", null, false);
    }

    static boolean hostMatches(ResolvedBackend backend, String overrideHost) {
        if (overrideHost == null || backend == null) {
            return false;
        }
        if (overrideHost.equalsIgnoreCase(backend.externalHost)) {
            return true;
        }
        String privateHost = UrlUtils.extractHostname(backend.privateEndpoint);
        return privateHost != null && overrideHost.equalsIgnoreCase(privateHost);
    }

    public static boolean isUnmatchedExternalOverride(ConversionContext ctx, String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        ResolvedBackend resolved = resolveBackendForUrl(ctx, url);
        if (resolved == null || resolved.type != BackendType.EXTERNAL) {
            return false;
        }
        String host = UrlUtils.extractHostname(url);
        if (host == null || ctx.resolvedBackends == null) {
            return true;
        }
        for (ResolvedBackend backend : ctx.resolvedBackends) {
            if (hostMatches(backend, host)) {
                return false;
            }
        }
        return true;
    }

    public static String buildReadmeNotes(ApiService service, PolicyFinder policyFinder,
            ConversionContext ctx) {
        Policy routing = policyFinder.findEnabled(service, "routing");
        if (routing == null) {
            return "";
        }
        List<RoutingRule> rules = parseRules(routing);
        List<String> bullets = new ArrayList<>();
        boolean anyJwt = false;
        for (RoutingRule rule : rules) {
            if (rule.hasJwtClaim()) {
                anyJwt = true;
            }
            for (MatchOp op : rule.operations()) {
                if ((op.kind() == MatchKind.HEADER || op.kind() == MatchKind.QUERY_ARG
                        || op.kind() == MatchKind.PATH)
                        && !isEqualityOp(op.op())) {
                    bullets.add("unsupported routing op `" + op.op() + "` on " + op.kind().name().toLowerCase(Locale.ROOT)
                            + " — match skipped");
                }
            }
            if (ctx != null && isUnmatchedExternalOverride(ctx, rule.url())) {
                String host = UrlUtils.extractHostname(rule.url());
                bullets.add("external override host `" + host
                        + "` requires a manual ServiceEntry (and TLS DestinationRule if needed)"
                        + " — not auto-generated for routing overrides");
            }
        }
        if (anyJwt) {
            bullets.add(0, "jwt_claim conditions are not convertible to Gateway API matches"
                    + " — no AuthPolicy claim→header bridge in P1; convertible sibling ops still emit");
        }
        if (bullets.isEmpty()) {
            return """

## Routing

`httproute.yaml` includes conditional rules mapped from 3scale `routing`
(header / query / path matches with override backends when present).
""";
        }
        StringBuilder body = new StringBuilder();
        for (String note : bullets.stream().distinct().toList()) {
            body.append("- ").append(note).append('\n');
        }
        return """

## WARNING: Routing conversion gaps

3scale `routing` was partially converted to HTTPRoute conditional rules.
Review before apply:

%s
JWT-claim routing has no Gateway API equivalent without Authorino claim→header.
External override hosts do not get a synthetic ServiceEntry in P1 — create mesh resources manually if needed.
""".formatted(body);
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
