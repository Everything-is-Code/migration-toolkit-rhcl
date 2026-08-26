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

/**
 * JaCoCo records conversion support coverage only through Quarkus-instrumented classes.
 * These tests complement the plain JUnit suites in this package.
 */
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
        assertTrue(files.get("README.md").contains("External Backend (External HTTPS Service)"));
        assertTrue(files.get("README.md").contains("Multiple backends (path-first)"));
        assertTrue(files.get("policy.yaml").contains("jwt-claim-check"));
    }

    @Test
    void convert_contributors_corsHeadersKeycloakAndAnonymous() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/api/items"));
        service.policies.add(ContributorTestFixtures.corsPolicy(Map.of(
                "allow_origin", List.of("https://app.example.com"),
                "allow_methods", List.of("GET", "POST"),
                "allow_headers", List.of("Authorization"),
                "allow_credentials", true,
                "max_age", 300)));
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                List.of(Map.of("header", "X-Req", "value", "in", "op", "add")),
                List.of(
                        Map.of("header", "X-Res", "value", "out", "op", "set"),
                        Map.of("header", "X-Del", "op", "delete"),
                        Map.of("header", "X-Liq", "value", "{{x}}", "value_type", "liquid"))));
        service.policies.add(ContributorTestFixtures.keycloakRoleCheckPolicy(List.of(
                Map.of("realm_roles", List.of(Map.of("name", "editor"))))));
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(5, 30, 60));
        service.policies.add(ContributorTestFixtures.retryPolicy(2));

        ConversionOptions options = ConversionSupportTestFixtures.conversionOptions();
        options.corsNative = true;
        options.retriesSupported = true;
        Map<String, String> files = conversionService.convert(service, ContributorTestFixtures.NAMESPACE, null, options);

        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("method: OPTIONS"));
        assertTrue(httproute.contains("timeouts:"));
        assertTrue(httproute.contains("retry:"));
        assertTrue(httproute.contains("liquid template"));

        String policy = files.get("policy.yaml");
        assertTrue(policy.contains("keycloak-role-check:"));
    }

    @Test
    void convert_contributors_anonymousAndIntrospection() {
        ApiService anon = ContributorTestFixtures.apiService();
        anon.policies.add(ContributorTestFixtures.anonymousAccessPolicy(
                "app_id_and_app_key", Map.of("app_id", "aid", "app_key", "akey")));
        ConversionOptions anonOpts = new ConversionOptions();
        anonOpts.anonymousTarget = "gateway";
        Map<String, String> anonFiles = conversionService.convert(
                anon, ContributorTestFixtures.NAMESPACE, null, anonOpts);
        assertTrue(anonFiles.get("policy.yaml").contains("anonymous:"));
        assertTrue(anonFiles.get("secret.yaml").contains("anonymous-credentials"));

        ApiService intro = ContributorTestFixtures.apiService();
        intro.policies.add(ContributorTestFixtures.tokenIntrospectionPolicy(
                "https://idp.example.com/introspect"));
        Map<String, String> introFiles = conversionService.convert(intro, ContributorTestFixtures.NAMESPACE);
        assertTrue(introFiles.get("policy.yaml").contains("oauth2-introspection:"));
        assertTrue(introFiles.get("secret.yaml").contains("oauth2-introspection"));
    }

    @Test
    void convert_contributors_apiKeyAndAppIdKey() {
        ApiService apiKey = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        assertTrue(conversionService.convert(apiKey, ContributorTestFixtures.NAMESPACE)
                .get("policy.yaml").contains("api-key-auth:"));

        ApiService appIdKey = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        appIdKey.applications.add(ContributorTestFixtures.application("app-1", "key-1"));
        assertTrue(conversionService.convert(appIdKey, ContributorTestFixtures.NAMESPACE)
                .get("policy.yaml").contains("app-id-key-auth:"));
    }

    @Test
    void convert_contributors_ipCheckOpaMode() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.policies.add(ContributorTestFixtures.ipCheckPolicy("blacklist",
                List.of("10.0.0.0/8", "192.168.1.1")));
        ConversionOptions options = new ConversionOptions();
        options.ipCheckMode = "authPolicyOpa";
        String policy = conversionService.convert(service, ContributorTestFixtures.NAMESPACE, null, options)
                .get("policy.yaml");
        assertTrue(policy.contains("ip-check:"));
        assertTrue(policy.contains("denied"));
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
        assertTrue(JwtClaimCheckSupport.buildNamedRule(parsed.patterns()).contains("jwt-claim-check"));

        String yaml = conversionYamlSupport.stripMigratedFromLabel("migrated-from: 3scale\napp: demo\n");
        assertFalse(yaml.contains("migrated-from: 3scale"));
        assertEquals("a\nb", ConversionYamlSupport.normalizeLineEndings("a\r\nb"));
    }
}
