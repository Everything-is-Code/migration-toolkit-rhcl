package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityServiceTest {

    private CompatibilityService service;

    // Default supported policies used in most tests
    private static final Set<String> DEFAULT_POLICIES = Set.of("3scale APIcast", "Upstream Connection");
    private static final Set<String> EMPTY_POLICIES = Set.of();

    @BeforeEach
    void setUp() {
        service = new CompatibilityService();
    }

    // ── Authentication checks ────────────────────────────────────────────────

    @Test
    void check_nullAuthentication_warningItem() {
        ApiService svc = basicService();
        svc.authentication = null;
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Authentication")));
    }

    @Test
    void check_jwtAuthentication_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("JWT")));
    }

    @Test
    void check_apiKeyAuthentication_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("apiKey");
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("API Key")));
    }

    @Test
    void check_appIdKeyAuthentication_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("appIdKey");
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("App ID")));
    }

    @Test
    void check_unknownAuthentication_warning() {
        ApiService svc = basicService();
        svc.authentication = auth("oauth2-custom");
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)));
    }

    // ── Policy checks ────────────────────────────────────────────────────────

    @Test
    void check_policyInSupportedList_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("cors"));
        // "cors" maps to "CORS Request Handling"
        CompatibilityResult result = service.check(svc, Set.of("CORS Request Handling"));
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("CORS")));
    }

    @Test
    void check_policyNotInSupportedList_warning() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("soap"));
        CompatibilityResult result = service.check(svc, EMPTY_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.contains("SOAP")));
    }

    @Test
    void check_urlRewritingInList_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("url_rewriting"));
        CompatibilityResult result = service.check(svc, Set.of("URL Rewriting"));
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("URL Rewriting")));
    }

    @Test
    void check_headerModificationInList_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("header_modification"));
        CompatibilityResult result = service.check(svc, Set.of("Header Modification"));
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("Header")));
    }

    @Test
    void check_headersAlias_supportedWhenHeaderModificationInList() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("headers"));
        CompatibilityResult result = service.check(svc, Set.of("Header Modification"));
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && "Header Modification".equals(i.name)));
    }

    /**
     * After PR1 converters land, fresh DEFAULT_SUPPORTED includes CORS + Header Modification.
     * Compatibility must mark those policies SUPPORTED when the default set is used.
     */
    @Test
    void check_pr1DefaultSupportedPolicies_corsAndHeaderModificationSupported() {
        // Mirrors frontend DEFAULT_SUPPORTED_POLICIES after PR1 land (CORS added).
        Set<String> pr1Defaults = Set.of(
                "3scale APIcast",
                "Header Modification",
                "Upstream Connection",
                "Logging",
                "Anonymous Access",
                "URL Rewriting",
                "3scale Auth Caching",
                "CORS Request Handling");

        ApiService corsSvc = basicService();
        corsSvc.authentication = auth("jwt");
        corsSvc.policies = List.of(enabledPolicy("cors"));
        CompatibilityResult corsResult = service.check(corsSvc, pr1Defaults);
        assertTrue(corsResult.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && "CORS Request Handling".equals(i.name)));

        ApiService headerSvc = basicService();
        headerSvc.authentication = auth("jwt");
        headerSvc.policies = List.of(enabledPolicy("header_modification"));
        CompatibilityResult headerResult = service.check(headerSvc, pr1Defaults);
        assertTrue(headerResult.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && "Header Modification".equals(i.name)));
    }

    /**
     * After PR2 converters land, DEFAULT_SUPPORTED includes IP Check.
     */
    @Test
    void check_pr2DefaultSupportedPolicies_ipCheckSupported() {
        Set<String> pr2Defaults = Set.of(
                "3scale APIcast",
                "Header Modification",
                "Upstream Connection",
                "Logging",
                "Anonymous Access",
                "URL Rewriting",
                "3scale Auth Caching",
                "CORS Request Handling",
                "IP Check");

        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("ip_check"));
        CompatibilityResult result = service.check(svc, pr2Defaults);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && "IP Check".equals(i.name)));
    }

    /**
     * After PR3 converters land, DEFAULT_SUPPORTED includes Edge Limiting.
     */
    @Test
    void check_pr3DefaultSupportedPolicies_edgeLimitingSupported() {
        Set<String> pr3Defaults = Set.of(
                "3scale APIcast",
                "Header Modification",
                "Upstream Connection",
                "Logging",
                "Anonymous Access",
                "URL Rewriting",
                "3scale Auth Caching",
                "CORS Request Handling",
                "IP Check",
                "Edge Limiting");

        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("edge_limiting"));
        CompatibilityResult result = service.check(svc, pr3Defaults);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && "Edge Limiting".equals(i.name)));
    }

    @Test
    void check_edgeLimiting_withoutSupportedList_warns() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("edge_limiting"));
        CompatibilityResult result = service.check(svc, Set.of("Header Modification", "Logging"));
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && "Edge Limiting".equals(i.name)));
    }

    @Test
    void check_customSupportedListWithoutCors_stillWarns() {
        // User override / saved custom list without CORS must keep WARNING until reset.
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of(enabledPolicy("cors"));
        CompatibilityResult result = service.check(svc, Set.of("Header Modification", "Logging"));
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && "CORS Request Handling".equals(i.name)));
    }

    @Test
    void check_disabledPoliciesIgnored() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        Policy p = new Policy();
        p.name = "soap";
        p.enabled = false;
        svc.policies = List.of(p);
        CompatibilityResult result = service.check(svc, EMPTY_POLICIES);
        // Disabled policy should produce no items
        assertTrue(result.items.stream().noneMatch(i -> "SOAP".equals(i.name)));
    }

    @Test
    void check_nullPolicies_noError() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = null;
        assertDoesNotThrow(() -> service.check(svc, DEFAULT_POLICIES));
    }

    @Test
    void check_emptyPolicies_noError() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.policies = List.of();
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertNotNull(result);
    }

    // ── Mapping rule checks ───────────────────────────────────────────────────

    @Test
    void check_nullMappingRules_warningItem() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.mappingRules = null;
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Mapping Rules")));
    }

    @Test
    void check_emptyMappingRules_warningItem() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.mappingRules = List.of();
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Mapping Rules")));
    }

    @Test
    void check_mappingRulesWithWildcard_warning() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.pattern = "/api/{?}";
        svc.mappingRules = List.of(rule);
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Mapping Rules")));
    }

    @Test
    void check_mappingRulesWithoutWildcard_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.pattern = "/api/users";
        svc.mappingRules = List.of(rule);
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.equals("Mapping Rules")));
    }

    @Test
    void check_multipleMappingRules_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.mappingRules = List.of(
                rule("GET", "/users"),
                rule("POST", "/users"),
                rule("DELETE", "/users/{id}")
        );
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.message.contains("3")));
    }

    // ── Backend checks ────────────────────────────────────────────────────────

    @Test
    void check_nullBackends_warningItem() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.backends = null;
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Backend")));
    }

    @Test
    void check_emptyBackends_warningItem() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.backends = List.of();
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "WARNING".equals(i.status)
                && i.name.equals("Backend")));
    }

    @Test
    void check_httpsBackend_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        Backend b = new Backend();
        b.name = "my-backend";
        b.privateEndpoint = "https://api.example.com";
        svc.backends = List.of(b);
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)
                && i.name.contains("TLS")));
    }

    @Test
    void check_httpBackend_supported() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        Backend b = new Backend();
        b.name = "http-backend";
        b.privateEndpoint = "http://api.internal";
        svc.backends = List.of(b);
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertTrue(result.items.stream().anyMatch(i -> "SUPPORTED".equals(i.status)));
    }

    // ── Score and level calculation ───────────────────────────────────────────

    @Test
    void check_allSupported_scoreHigh() {
        ApiService svc = fullSupportedService();
        CompatibilityResult result = service.check(svc, Set.of("CORS Request Handling"));
        assertEquals("HIGH", result.level);
        assertTrue(result.score >= 80);
    }

    @Test
    void check_unknownPolicyNotInList_reducesScore() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.mappingRules = List.of(rule("GET", "/api"));
        svc.backends = List.of(backend("http://svc"));
        svc.policies = List.of(enabledPolicy("soap"));
        CompatibilityResult result = service.check(svc, EMPTY_POLICIES);
        assertTrue(result.score < 100, "Policy not in list should reduce score");
    }

    @Test
    void check_serviceIdAndNamePreserved() {
        ApiService svc = basicService();
        svc.id = "test-id-99";
        svc.name = "My Test Service";
        svc.authentication = auth("jwt");
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertEquals("test-id-99", result.serviceId);
        assertEquals("My Test Service", result.serviceName);
    }

    @Test
    void check_mediumLevel_whenMixedResults() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "Mixed";
        Authentication auth = new Authentication();
        auth.type = "appIdKey";
        svc.authentication = auth;
        svc.mappingRules = null;
        svc.backends = null;
        svc.policies = null;
        CompatibilityResult result = service.check(svc, DEFAULT_POLICIES);
        assertNotNull(result.level);
        assertTrue(result.score >= 0 && result.score <= 100);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ApiService basicService() {
        ApiService svc = new ApiService();
        svc.id = "svc-1";
        svc.name = "Test Service";
        return svc;
    }

    private ApiService fullSupportedService() {
        ApiService svc = basicService();
        svc.authentication = auth("jwt");
        svc.mappingRules = List.of(rule("GET", "/api/users"), rule("POST", "/api/users"));
        svc.backends = List.of(backend("https://api.example.com"));
        svc.policies = List.of(enabledPolicy("cors"));
        return svc;
    }

    private Authentication auth(String type) {
        Authentication a = new Authentication();
        a.type = type;
        return a;
    }

    private Policy enabledPolicy(String name) {
        Policy p = new Policy();
        p.name = name;
        p.enabled = true;
        return p;
    }

    private MappingRule rule(String method, String pattern) {
        MappingRule r = new MappingRule();
        r.httpMethod = method;
        r.pattern = pattern;
        return r;
    }

    private Backend backend(String endpoint) {
        Backend b = new Backend();
        b.name = "backend";
        b.privateEndpoint = endpoint;
        return b;
    }
}
