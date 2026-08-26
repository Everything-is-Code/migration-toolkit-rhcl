package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helpers for 3scale {@code upstream} policy conversion.
 * <p>
 * Catch-all regex allowlist (global override): {@code .*}, {@code ^/.*$}, {@code /},
 * {@code ^/}, blank/empty. Empty {@code rules} with a top-level {@code url} is also global.
 */
public final class UpstreamSupport {

    public enum MatchType {
        PATH_PREFIX,
        REGULAR_EXPRESSION
    }

    public record MatchApproximation(MatchType type, String value) {}

    public record UpstreamRule(String regex, String url, MatchApproximation match) {
        public boolean convertible() {
            return match != null && url != null && !url.isBlank();
        }
    }

    private static final Pattern LOOKAROUND = Pattern.compile("\\(\\?[:=!<]");
    private static final Pattern BACKREF = Pattern.compile("\\\\[1-9]");
    private static final Pattern POSSESSIVE = Pattern.compile("[*+?]\\+");
    private static final Pattern SIMPLE_PREFIX = Pattern.compile("^\\^?/[^$^*+?.\\\\|()\\[\\]]*$");

    private UpstreamSupport() {
    }

    public static boolean isGlobal(Policy upstream) {
        if (upstream == null || upstream.configuration == null) {
            return false;
        }
        List<UpstreamRule> rules = parseRules(upstream);
        String topUrl = stringOrNull(upstream.configuration.get("url"));
        if (rules.isEmpty()) {
            return topUrl != null && !topUrl.isBlank();
        }
        return rules.size() == 1 && isCatchAllRegex(rules.get(0).regex());
    }

    public static boolean allRulesConvertible(Policy upstream) {
        if (upstream == null) {
            return false;
        }
        List<UpstreamRule> rules = parseRules(upstream);
        if (isGlobal(upstream)) {
            String url = globalUrl(upstream);
            return url != null && !url.isBlank();
        }
        if (rules.isEmpty()) {
            return false;
        }
        return rules.stream().allMatch(UpstreamRule::convertible);
    }

    public static List<UpstreamRule> parseRules(Policy upstream) {
        List<UpstreamRule> out = new ArrayList<>();
        if (upstream == null || upstream.configuration == null) {
            return out;
        }
        Object rulesRaw = upstream.configuration.get("rules");
        if (!(rulesRaw instanceof List<?> rules)) {
            return out;
        }
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) ruleMap;
            String regex = stringOrNull(rule.get("regex"));
            if (regex == null) {
                regex = "";
            }
            String url = stringOrNull(rule.get("url"));
            MatchApproximation match = (url != null && !url.isBlank()) ? approximateRegex(regex) : null;
            out.add(new UpstreamRule(regex, url, match));
        }
        return out;
    }

    public static String globalUrl(Policy upstream) {
        if (upstream == null || upstream.configuration == null) {
            return null;
        }
        List<UpstreamRule> rules = parseRules(upstream);
        if (rules.isEmpty()) {
            return stringOrNull(upstream.configuration.get("url"));
        }
        if (rules.size() == 1 && isCatchAllRegex(rules.get(0).regex())) {
            return rules.get(0).url();
        }
        return null;
    }

    /**
     * Catch-all allowlist: {@code .*}, {@code ^/.*$}, {@code /}, {@code ^/}, blank.
     */
    public static boolean isCatchAllRegex(String regex) {
        if (regex == null) {
            return true;
        }
        String r = regex.trim();
        return r.isEmpty()
                || ".*".equals(r)
                || "^/.*$".equals(r)
                || "/".equals(r)
                || "^/".equals(r);
    }

    public static MatchApproximation approximateRegex(String regex) {
        if (regex == null) {
            return null;
        }
        String r = regex.trim();
        if (r.isEmpty()) {
            return new MatchApproximation(MatchType.PATH_PREFIX, "/");
        }
        if (LOOKAROUND.matcher(r).find() || BACKREF.matcher(r).find() || POSSESSIVE.matcher(r).find()) {
            return null;
        }
        if (isSimplePathPrefix(r)) {
            return new MatchApproximation(MatchType.PATH_PREFIX, toPathPrefixValue(r));
        }
        return new MatchApproximation(MatchType.REGULAR_EXPRESSION, r);
    }

    public static ResolvedBackend resolveOverrideBackend(String productName, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return new BackendResolver().resolveOne(productName, url.trim(), null, "/", null, false);
    }

    public static String buildReadmeNotes(ApiService service, PolicyFinder policyFinder) {
        Policy upstream = policyFinder.findEnabled(service, "upstream");
        if (upstream == null) {
            return "";
        }
        List<String> bullets = new ArrayList<>();
        List<UpstreamRule> rules = parseRules(upstream);
        for (UpstreamRule rule : rules) {
            if (!rule.convertible() && !isCatchAllRegex(rule.regex())) {
                bullets.add("skipped non-approximable regex `" + rule.regex() + "` — no HTTPRoute rule emitted");
            }
        }
        List<String> overrideUrls = collectOverrideUrls(upstream, rules);
        for (String url : overrideUrls) {
            ResolvedBackend override = resolveOverrideBackend(
                    service.systemName != null ? service.systemName : service.name, url);
            if (override != null && override.type == BackendType.EXTERNAL) {
                bullets.add("external override host `" + override.externalHost
                        + "` requires a manual ServiceEntry (and TLS DestinationRule if needed)"
                        + " — not auto-generated for upstream overrides");
            }
            if (hasSchemeMismatch(service, url)) {
                bullets.add("scheme mismatch between product backends and override `" + url
                        + "` (HTTP↔HTTPS) — verify TLS origination manually");
            }
        }
        if (bullets.isEmpty() && allRulesConvertible(upstream)) {
            return """

## Upstream

`httproute.yaml` includes backend overrides mapped from 3scale `upstream`
(global override and/or path-scoped approximable rules).
""";
        }
        if (bullets.isEmpty()) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        for (String note : bullets.stream().distinct().toList()) {
            body.append("- ").append(note).append('\n');
        }
        return """

## WARNING: Upstream conversion gaps

3scale `upstream` was partially converted to HTTPRoute backend overrides.
Review before apply:

%s
Non-approximable PCRE is not silently treated as supported. External override hosts
do not get a synthetic ServiceEntry in P1 — create mesh resources manually if needed.
""".formatted(body);
    }

    private static List<String> collectOverrideUrls(Policy upstream, List<UpstreamRule> rules) {
        List<String> urls = new ArrayList<>();
        if (isGlobal(upstream)) {
            String url = globalUrl(upstream);
            if (url != null) {
                urls.add(url);
            }
            return urls;
        }
        for (UpstreamRule rule : rules) {
            if (rule.url() != null && !rule.url().isBlank()) {
                urls.add(rule.url());
            }
        }
        return urls;
    }

    private static boolean hasSchemeMismatch(ApiService service, String overrideUrl) {
        String overrideScheme = schemeOf(overrideUrl);
        if (overrideScheme == null || service.backends == null) {
            return false;
        }
        for (Backend backend : service.backends) {
            String productScheme = schemeOf(backend.privateEndpoint);
            if (productScheme != null && !productScheme.equals(overrideScheme)) {
                return true;
            }
        }
        return false;
    }

    private static String schemeOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("https://")) {
            return "https";
        }
        if (trimmed.startsWith("http://")) {
            return "http";
        }
        return null;
    }

    private static boolean isSimplePathPrefix(String regex) {
        if (regex.contains(".*") || regex.contains(".+") || regex.contains("|")
                || regex.contains("(") || regex.contains("[")) {
            return false;
        }
        return SIMPLE_PREFIX.matcher(regex).matches();
    }

    private static String toPathPrefixValue(String regex) {
        String value = regex;
        if (value.startsWith("^")) {
            value = value.substring(1);
        }
        if (value.endsWith("$")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return "/";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
