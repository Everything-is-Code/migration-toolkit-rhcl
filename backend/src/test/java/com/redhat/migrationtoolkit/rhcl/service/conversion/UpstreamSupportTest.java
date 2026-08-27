package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamSupportTest {

    @Test
    void isGlobal_emptyRulesWithTopLevelUrl() {
        Policy policy = upstreamPolicy(Map.of(
                "url", "https://override.example.com",
                "rules", List.of()));
        assertTrue(UpstreamSupport.isGlobal(policy));
    }

    @Test
    void isGlobal_singleCatchAllRegex() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(Map.of("regex", ".*", "url", "https://override.example.com"))));
        assertTrue(UpstreamSupport.isGlobal(policy));
    }

    @Test
    void isGlobal_falseForPathScopedRules() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                        Map.of("regex", "^/v2", "url", "https://v2.example.com"))));
        assertFalse(UpstreamSupport.isGlobal(policy));
    }

    @Test
    void approximateRegex_pathPrefixForSimplePrefix() {
        UpstreamSupport.MatchApproximation match = UpstreamSupport.approximateRegex("^/api");
        assertEquals(UpstreamSupport.MatchType.PATH_PREFIX, match.type());
        assertEquals("/api", match.value());
    }

    @Test
    void approximateRegex_regularExpressionForComplexButSafe() {
        UpstreamSupport.MatchApproximation match = UpstreamSupport.approximateRegex("^/api/.*");
        assertEquals(UpstreamSupport.MatchType.REGULAR_EXPRESSION, match.type());
        assertEquals("^/api/.*", match.value());
    }

    @Test
    void approximateRegex_nonApproximableLookaround() {
        assertNull(UpstreamSupport.approximateRegex("^/api(?=/)"));
    }

    @Test
    void approximateRegex_nonApproximableBackref() {
        assertNull(UpstreamSupport.approximateRegex("^(/(.*))/\\1$"));
    }

    @Test
    void approximateRegex_nonApproximablePossessive() {
        assertNull(UpstreamSupport.approximateRegex("^/api[*+?]+"));
        assertNull(UpstreamSupport.approximateRegex("^/files/.++"));
    }

    @Test
    void globalUrl_fromTopLevelWhenRulesEmpty() {
        Policy policy = upstreamPolicy(Map.of(
                "url", "https://override.example.com",
                "rules", List.of()));
        assertEquals("https://override.example.com", UpstreamSupport.globalUrl(policy));
    }

    @Test
    void globalUrl_fromSingleCatchAllRule() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(Map.of("regex", ".*", "url", "https://catch.example.com"))));
        assertEquals("https://catch.example.com", UpstreamSupport.globalUrl(policy));
    }

    @Test
    void globalUrl_nullForPathScopedRules() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                        Map.of("regex", "^/v2", "url", "https://v2.example.com"))));
        assertNull(UpstreamSupport.globalUrl(policy));
    }

    @Test
    void allRulesConvertible_trueWhenAllApprox() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                        Map.of("regex", "^/v2/.*", "url", "https://v2.example.com"))));
        assertTrue(UpstreamSupport.allRulesConvertible(policy));
    }

    @Test
    void allRulesConvertible_falseWhenAnyNonApprox() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                        Map.of("regex", "^/api(?=!)", "url", "https://bad.example.com"))));
        assertFalse(UpstreamSupport.allRulesConvertible(policy));
    }

    @Test
    void resolveOverrideBackend_doesNotMutateCallerList() {
        ResolvedBackend backend = UpstreamSupport.resolveOverrideBackend(
                "demo-api", "https://override.example.com:8443");
        assertNotNull(backend);
        assertEquals(BackendType.EXTERNAL, backend.type);
        assertEquals("override.example.com", backend.externalHost);
        assertEquals(8443, backend.port);
    }

    @Test
    void allRulesConvertible_falseWhenGlobalUrlBlank() {
        Policy policy = upstreamPolicy(Map.of("url", "  ", "rules", List.of()));
        assertFalse(UpstreamSupport.allRulesConvertible(policy));
    }

    @Test
    void allRulesConvertible_falseWhenRulesEmptyAndNoTopLevelUrl() {
        Policy policy = upstreamPolicy(Map.of("rules", List.of()));
        assertFalse(UpstreamSupport.allRulesConvertible(policy));
    }

    @Test
    void parseRules_skipsNonMapEntriesAndBlankUrlRules() {
        Policy policy = upstreamPolicy(Map.of(
                "rules", List.of(
                        "not-a-map",
                        Map.of("regex", "^/v1"),
                        Map.of("regex", "^/ok", "url", "https://ok.example.com"))));
        List<UpstreamSupport.UpstreamRule> rules = UpstreamSupport.parseRules(policy);
        assertEquals(2, rules.size());
        assertNull(rules.get(0).match());
        assertNotNull(rules.get(1).match());
    }

    @Test
    void approximateRegex_blankRegex_returnsRootPathPrefix() {
        UpstreamSupport.MatchApproximation match = UpstreamSupport.approximateRegex("   ");
        assertEquals(UpstreamSupport.MatchType.PATH_PREFIX, match.type());
        assertEquals("/", match.value());
    }

    @Test
    void isCatchAllRegex_acceptsAllowlistedPatterns() {
        assertTrue(UpstreamSupport.isCatchAllRegex(null));
        assertTrue(UpstreamSupport.isCatchAllRegex("  "));
        assertTrue(UpstreamSupport.isCatchAllRegex("^/.*$"));
        assertTrue(UpstreamSupport.isCatchAllRegex("/"));
        assertTrue(UpstreamSupport.isCatchAllRegex("^/"));
        assertFalse(UpstreamSupport.isCatchAllRegex("^/v1"));
    }

    @Test
    void buildReadmeNotes_emptyWhenUpstreamAbsent() {
        assertEquals("", UpstreamSupport.buildReadmeNotes(ContributorTestFixtures.apiService(), new PolicyFinder()));
    }

    @Test
    void buildReadmeNotes_emptyWhenGlobalUrlBlankAndNoGapBullets() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(upstreamPolicy(Map.of("url", " ", "rules", List.of())));

        assertEquals("", UpstreamSupport.buildReadmeNotes(service, new PolicyFinder()));
    }

    @Test
    void resolveOverrideBackend_nullForBlankUrl() {
        assertNull(UpstreamSupport.resolveOverrideBackend("demo-api", "   "));
    }

    @Test
    void buildReadmeNotes_skippedRegexAndExternalSeAndScheme() {
        ApiService service = ContributorTestFixtures.apiService();
        // product backend is http://api.example.com:8080 (http)
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/ok", "url", "https://ok.example.com"),
                        Map.of("regex", "^/api(?=!)", "url", "https://skip.example.com")))));

        String notes = UpstreamSupport.buildReadmeNotes(service, new PolicyFinder());

        assertTrue(notes.contains("## WARNING: Upstream conversion gaps"));
        assertTrue(notes.contains("skipped") || notes.contains("^/api(?=!)"));
        assertTrue(notes.contains("ServiceEntry"));
        assertTrue(notes.contains("scheme") || notes.contains("HTTP") || notes.contains("HTTPS"));
        assertFalse(notes.contains("serviceentry.yaml"));
    }

    @Test
    void buildReadmeNotes_happyPath_whenAllConvertible() {
        ApiService service = ContributorTestFixtures.apiService();
        // Internal (no-dot host) + matching http scheme → no gap bullets
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "http://upstream-svc:8080"),
                        Map.of("regex", "^/v2", "url", "http://other-svc:8080")))));

        String notes = UpstreamSupport.buildReadmeNotes(service, new PolicyFinder());

        assertTrue(notes.contains("## Upstream"));
        assertFalse(notes.contains("## WARNING"));
        assertTrue(notes.contains("httproute.yaml"));
    }

    private static Policy upstreamPolicy(Map<String, Object> configuration) {
        Policy policy = new Policy();
        policy.name = "upstream";
        policy.enabled = true;
        policy.configuration = new HashMap<>(configuration);
        return policy;
    }
}
