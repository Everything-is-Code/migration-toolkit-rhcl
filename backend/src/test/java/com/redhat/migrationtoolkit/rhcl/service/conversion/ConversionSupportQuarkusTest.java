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
        assertTrue(JwtClaimCheckSupport.buildNamedRule(parsed.patterns()).contains("jwt-claim-check"));

        String yaml = conversionYamlSupport.stripMigratedFromLabel("migrated-from: 3scale\napp: demo\n");
        assertFalse(yaml.contains("migrated-from: 3scale"));
        assertEquals("a\nb", ConversionYamlSupport.normalizeLineEndings("a\r\nb"));
    }
}
