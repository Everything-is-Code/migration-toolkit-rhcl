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

    private static Policy upstreamPolicy(Map<String, Object> configuration) {
        Policy policy = new Policy();
        policy.name = "upstream";
        policy.enabled = true;
        policy.configuration = new HashMap<>(configuration);
        return policy;
    }
}
