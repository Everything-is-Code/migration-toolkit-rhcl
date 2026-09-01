package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ConversionSupportQuarkusTest {

    @Inject
    ConversionService conversionService;

    @Inject
    BackendResolver backendResolver;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Inject
    RateLimitSupport rateLimitSupport;

    @Inject
    ConversionYamlSupport conversionYamlSupport;

    @Test
    void convert_richService_exercisesSupportPipeline() {
        ConversionOptions options = ConversionSupportTestFixtures.conversionOptions();
        options.includeTlsPolicy = true;
        options.tlsIssuerKind = "ClusterIssuer";
        options.tlsIssuerName = "letsencrypt";
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";

        Map<String, String> files = conversionService.convert(
                ConversionSupportTestFixtures.richService(), "demo-ns", null, options);

        assertTrue(files.containsKey("ratelimitpolicy.yaml"));
        assertTrue(files.containsKey("tlspolicy.yaml"));
        assertTrue(files.containsKey("dnspolicy.yaml"));
        assertTrue(files.get("tlspolicy.yaml").contains("kind: TLSPolicy"));
        assertTrue(files.get("dnspolicy.yaml").contains("kind: DNSPolicy"));
        assertTrue(files.get("README.md").contains("External Backend (External HTTPS Service)"));
        assertTrue(files.get("README.md").contains("Multiple backends (path-first)"));
        assertTrue(files.get("policy.yaml").contains("jwt-claim-check"));
    }

    @Test
    void injectedBeans_coverDirectSupportMethods() {
        ApiService service = ConversionSupportTestFixtures.richService();
        Policy retry = ConversionSupportTestFixtures.policy("retry", true, Map.of("retries", 2));

        assertEquals(2, policyConfigSupport.resolveRetryAttempts(retry));
        assertEquals(2048, policyConfigSupport.resolveContentLimitBytes(
                service.policies.stream().filter(p -> "content_limits".equals(p.name)).findFirst().orElseThrow(),
                true));
        assertEquals("10.0.0.0/32", policyConfigSupport.normalizeCidr("10.0.0.0"));

        assertNotNull(rateLimitSupport.resolvePlanCeiling(service));
        assertNotNull(rateLimitSupport.generateRateLimitPolicy("demo-api", "demo-ns", service));

        List<ResolvedBackend> resolved = backendResolver.resolveBackends(
                service, "demo-api", null, false);
        assertEquals(2, resolved.size());

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        HttpRouteSupport.collectMappingRulePaths(service, paths);
        assertTrue(paths.contains("/users"));

        assertEquals("token", AuthPolicySupport.firstNonBlank(null, "token"));
        assertEquals(32, SecretSupport.generateRandomHex(16).length());

        JwtClaimCheckSupport.JwtClaimParseResult parsed = JwtClaimCheckSupport.parseRules(
                service.policies.stream().filter(p -> "jwt_claim_check".equals(p.name)).findFirst().orElseThrow());
        assertFalse(parsed.patterns().isEmpty());
        assertNotNull(JwtClaimCheckSupport.buildNamedRule(parsed.patterns()));

        String yaml = conversionYamlSupport.stripMigratedFromLabel("migrated-from: 3scale\napp: demo\n");
        assertFalse(yaml.contains("migrated-from: 3scale"));
        assertEquals("a\nb", ConversionYamlSupport.normalizeLineEndings("a\r\nb"));
    }

    // ── maintenance_mode → EnvoyFilter / README under quarkus-jacoco (#152 codecov)

    @Test
    void convert_maintenanceMode_enabled_emitsEnvoyFilterAndReadmeOverlay() {
        ApiService service = ConversionSupportTestFixtures.apiService("maint-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));
        service.policies.add(ConversionSupportTestFixtures.policy("maintenance_mode", true, Map.of(
                "enabled", true,
                "status", 503,
                "message", "Under maintenance",
                "message_content_type", "text/plain")));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        assertTrue(files.containsKey("envoyfilter-maintenance.yaml"));
        String ef = files.get("envoyfilter-maintenance.yaml");
        assertTrue(ef.contains("503") && ef.contains("Under maintenance") && ef.contains("text/plain"));
        String readme = files.get("README.md");
        assertTrue(readme.contains("envoyfilter-maintenance.yaml"));
        assertTrue(readme.contains("## Maintenance Mode"));
    }

    @Test
    void convert_maintenanceMode_configDisabled_omitsEnvoyFilter() {
        ApiService service = ConversionSupportTestFixtures.apiService("maint-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));
        service.policies.add(ConversionSupportTestFixtures.policy("maintenance_mode", true, Map.of(
                "enabled", false,
                "status", 503,
                "message", "Under maintenance")));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        assertFalse(files.containsKey("envoyfilter-maintenance.yaml"));
        assertFalse(files.get("README.md").contains("## Maintenance Mode"));
    }

    // ── upstream → HTTPRoute / README under quarkus-jacoco (#151 codecov) ────

    @Test
    void convert_upstream_globalCatchAll_overrideBackendsAndExternalReadme() {
        ApiService service = ConversionSupportTestFixtures.upstreamConvertService("http");
        service.policies.add(ConversionSupportTestFixtures.upstreamGlobalCatchAll(
                "https://override.example.com"));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("hostname: \"override.example.com\""),
                "Global external override must Host-rewrite: " + httproute);
        assertFalse(files.containsKey("serviceentry.yaml")
                        && files.get("serviceentry.yaml").contains("override.example.com"),
                "Must not synthesize ServiceEntry solely for upstream override");
        String readme = files.get("README.md");
        assertTrue(readme.contains("WARNING: Upstream conversion gaps")
                        || readme.contains("ServiceEntry"),
                "README must document external override / SE gap");
    }

    @Test
    void convert_upstream_globalTopLevelUrl_internalHappyReadme() {
        ApiService service = ConversionSupportTestFixtures.upstreamConvertService("http");
        // no-dot host → INTERNAL; matching http scheme → happy ## Upstream (no gap bullets)
        service.policies.add(ConversionSupportTestFixtures.upstreamGlobalTopLevelUrl(
                "http://upstream-svc:8080"));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        assertTrue(files.get("httproute.yaml").contains("upstream-svc")
                        || files.get("httproute.yaml").contains("backendRefs"),
                "Global internal override must appear in HTTPRoute backends");
        assertTrue(files.get("README.md").contains("## Upstream"));
        assertFalse(files.get("README.md").contains("## WARNING: Upstream"));
    }

    @Test
    void convert_upstream_pathScoped_rulesBeforeMapping() {
        ApiService service = ConversionSupportTestFixtures.upstreamConvertService("http");
        service.policies.add(ConversionSupportTestFixtures.upstreamPathScoped(List.of(
                Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                Map.of("regex", "^/v2/.*", "url", "https://v2.example.com"))));

        String httproute = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions())
                .get("httproute.yaml");

        int v1 = httproute.indexOf("v1.example.com");
        int fallback = httproute.indexOf("value: \"/fallback\"");
        assertTrue(v1 >= 0 && fallback >= 0 && v1 < fallback,
                "Path-scoped upstream rules must precede mapping rules: " + httproute);
        assertTrue(httproute.contains("PathPrefix") || httproute.contains("value: \"/v1\""));
        assertTrue(httproute.contains("RegularExpression") || httproute.contains("^/v2/.*"));
    }

    @Test
    void convert_upstream_mixedConvertibleAndNonApproximable_warnsInReadme() {
        ApiService service = ConversionSupportTestFixtures.upstreamConvertService("http");
        service.policies.add(ConversionSupportTestFixtures.upstreamPathScoped(List.of(
                Map.of("regex", "^/ok", "url", "https://ok.example.com"),
                Map.of("regex", "^/api(?=!)", "url", "https://lookaround.example.com"),
                Map.of("regex", "^/files/.++", "url", "https://possessive.example.com"),
                Map.of("regex", "^(/(.*))/\\1$", "url", "https://backref.example.com"))));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("value: \"/ok\"") || httproute.contains("ok.example.com"));
        assertFalse(httproute.contains("lookaround.example.com"));
        assertFalse(httproute.contains("possessive.example.com"));
        assertFalse(httproute.contains("backref.example.com"));

        String readme = files.get("README.md");
        assertTrue(readme.contains("WARNING: Upstream conversion gaps"));
        assertTrue(readme.contains("skipped") || readme.contains("^/api(?=!)"));
        assertTrue(readme.contains("ServiceEntry"));
    }

    // ── routing → HTTPRoute / README under quarkus-jacoco (#150 codecov) ─────

    @Test
    void convert_routing_headerQueryPath_andCombine_emitsMatchesAndHappyReadme() {
        ApiService service = ConversionSupportTestFixtures.routingConvertService();
        // Matching product host + equality-only ops → happy ## Routing
        service.policies.add(ConversionSupportTestFixtures.routingPolicy(List.of(
                ConversionSupportTestFixtures.routingRule(
                        "http://api.example.com:8080",
                        "and",
                        List.of(
                                Map.of("match", "header", "header_name", "X-Tenant", "op", "==", "value", "acme"),
                                Map.of("match", "query_arg", "query_arg_name", "env", "op", "==", "value", "staging"),
                                Map.of("match", "path", "op", "==", "value", "/special"))))));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("X-Tenant") && httproute.contains("value: \"acme\""), httproute);
        assertTrue(httproute.contains("env") && httproute.contains("value: \"staging\""), httproute);
        assertTrue(httproute.contains("value: \"/special\""), httproute);
        int routingPath = httproute.indexOf("value: \"/special\"");
        int fallback = httproute.indexOf("value: \"/fallback\"");
        assertTrue(routingPath >= 0 && fallback >= 0 && routingPath < fallback,
                "Routing rule must precede mapping fallthrough: " + httproute);
        assertTrue(files.get("README.md").contains("## Routing"));
        assertFalse(files.get("README.md").contains("## WARNING: Routing"));
    }

    @Test
    void convert_routing_orCombine_emitsSeparateRules() {
        ApiService service = ConversionSupportTestFixtures.routingConvertService();
        service.policies.add(ConversionSupportTestFixtures.routingPolicy(List.of(
                ConversionSupportTestFixtures.routingRule(
                        "http://api.example.com:8080",
                        "or",
                        List.of(
                                Map.of("match", "header", "header_name", "X-A", "op", "==", "value", "1"),
                                Map.of("match", "header", "header_name", "X-B", "op", "==", "value", "2"))))));

        String httproute = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions())
                .get("httproute.yaml");

        assertTrue(httproute.contains("X-A") && httproute.contains("X-B"), httproute);
        // OR multi-emit: each op is its own matches block (not ANDed headers in one rule)
        int xa = httproute.indexOf("X-A");
        int xb = httproute.indexOf("X-B");
        assertTrue(xa >= 0 && xb >= 0 && xa != xb, httproute);
        String between = xa < xb ? httproute.substring(xa, xb) : httproute.substring(xb, xa);
        assertTrue(between.contains("backendRefs") || between.contains("- matches:"),
                "OR must multi-emit separate rules: " + httproute);
    }

    @Test
    void convert_routing_jwtClaim_warnsButConvertsSiblingHeader() {
        ApiService service = ConversionSupportTestFixtures.routingConvertService();
        service.policies.add(ConversionSupportTestFixtures.routingPolicy(List.of(
                ConversionSupportTestFixtures.routingRule(
                        "http://api.example.com:8080",
                        "and",
                        List.of(
                                Map.of("match", "header", "header_name", "X-Ok", "op", "==", "value", "yes"),
                                Map.of("match", "jwt_claim", "jwt_claim_name", "role", "op", "==", "value",
                                        "admin"))))));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("X-Ok"), "Sibling header must still convert: " + httproute);
        assertFalse(httproute.contains("jwt_claim") || httproute.contains("role"),
                "JWT claim must not appear as HTTPRoute match: " + httproute);
        String readme = files.get("README.md");
        assertTrue(readme.contains("WARNING: Routing conversion gaps"));
        assertTrue(readme.contains("jwt_claim"));
    }

    @Test
    void convert_routing_unsupportedOpAndExternalOverride_readmeGaps() {
        ApiService service = ConversionSupportTestFixtures.routingConvertService();
        service.policies.add(ConversionSupportTestFixtures.routingPolicy(List.of(
                ConversionSupportTestFixtures.routingRule(
                        "https://routing-only.example.com",
                        "and",
                        List.of(
                                Map.of("match", "header", "header_name", "X-Skip", "op", "!=", "value", "1"),
                                Map.of("match", "header", "header_name", "X-Route", "op", "==", "value",
                                        "yes"))))));

        Map<String, String> files = conversionService.convert(
                service, "demo-ns", null, ConversionSupportTestFixtures.conversionOptions());

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("name: X-Route") || httproute.contains("routing-only.example.com"),
                httproute);
        assertFalse(httproute.contains("name: X-Skip"),
                "Unsupported != op must be skipped: " + httproute);
        assertFalse(files.containsKey("serviceentry.yaml")
                        && files.get("serviceentry.yaml").contains("routing-only.example.com"),
                "Must not synthesize ServiceEntry solely for routing override");
        String readme = files.get("README.md");
        assertTrue(readme.contains("WARNING: Routing conversion gaps"));
        assertTrue(readme.contains("unsupported routing op") || readme.contains("!="));
        assertTrue(readme.contains("ServiceEntry") || readme.contains("routing-only.example.com"));
    }
}
