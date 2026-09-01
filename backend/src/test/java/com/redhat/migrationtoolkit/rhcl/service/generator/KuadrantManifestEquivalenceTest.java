package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiKeyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiKeySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductSpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyRules;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.DnsPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.DnsPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.IssuerRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.LimitDefinition;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ProviderRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.Rate;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RequestedBy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.SecretRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicySpec;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.EmptyAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.JwtAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.support.MapEquivalenceSupport;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Golden round-trip: typed manifest records must serialize to the same structure as current generators.
 */
class KuadrantManifestEquivalenceTest {

    private final ManifestSerializer serializer = new ManifestSerializer();
    private final TlsPolicyGenerator tlsPolicyGenerator = new TlsPolicyGenerator();
    private final DnsPolicyGenerator dnsPolicyGenerator = new DnsPolicyGenerator();
    private final ApiProductGenerator apiProductGenerator = new ApiProductGenerator();
    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();
    private final RateLimitPolicyGenerator rateLimitPolicyGenerator = new RateLimitPolicyGenerator();

    @Test
    void tlsPolicy_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("TLS API", "tls-api");
        var options = new ConversionOptions();
        options.includeTlsPolicy = true;
        options.tlsIssuerKind = "ClusterIssuer";
        options.tlsIssuerName = "letsencrypt-staging";
        var ctx = GeneratorTestSupport.context(service, options);

        Map<String, Object> fromGenerator = YamlAssertions.parse(tlsPolicyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(tlsPolicyManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void dnsPolicy_withoutProvider_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        var options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        var ctx = GeneratorTestSupport.context(service, options);

        Map<String, Object> fromGenerator = YamlAssertions.parse(dnsPolicyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(dnsPolicyManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void dnsPolicy_withProvider_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("DNS API", "dns-api");
        var options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "api.example.com";
        options.dnsProviderSecretName = "dns-credentials";
        var ctx = GeneratorTestSupport.context(service, options);

        Map<String, Object> fromGenerator = YamlAssertions.parse(dnsPolicyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(dnsPolicyManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void apiProduct_quotedDescription_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Quoted API", "quoted-api");
        service.description = "API with \"quotes\" in description";
        var ctx = GeneratorTestSupport.context(service);

        Map<String, Object> fromGenerator = YamlAssertions.parse(apiProductGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(apiProductManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void apiProduct_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Product API", "product-api");
        var ctx = GeneratorTestSupport.context(service);

        Map<String, Object> fromGenerator = YamlAssertions.parse(apiProductGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(apiProductManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void apiKey_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Key API", "key-api");
        service.authentication = GeneratorTestSupport.apiKeyAuth();
        var ctx = GeneratorTestSupport.context(service);

        Map<String, Object> fromGenerator = YamlAssertions.parse(apiKeyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(apiKeyManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void authPolicy_emptyAuthentication_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Auth API", "auth-api");
        var ctx = GeneratorTestSupport.context(service);

        String fromGenerator = generateAuthPolicy(ctx, new EmptyAuthenticationContributor());
        Map<String, Object> fromGeneratorMap = YamlAssertions.parse(fromGenerator);
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(authPolicyManifest(ctx, null)));

        MapEquivalenceSupport.assertEquivalent(fromGeneratorMap, fromRecord);
    }

    @Test
    void authPolicy_jwtAuthentication_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("JWT API", "jwt-api");
        Authentication auth = new Authentication();
        auth.type = "jwt";
        auth.oidcIssuerEndpoint = "https://sso.example.com/realms/demo";
        service.authentication = auth;
        var ctx = GeneratorTestSupport.context(service);

        String fromGenerator = generateAuthPolicy(ctx, new JwtAuthenticationContributor());
        Map<String, Object> fromGeneratorMap = YamlAssertions.parse(fromGenerator);
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(authPolicyManifest(ctx, auth)));

        MapEquivalenceSupport.assertEquivalent(fromGeneratorMap, fromRecord);
    }

    @Test
    void rateLimitPolicy_edgeLimiting_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Rate API", "rate-api");
        service.policies = List.of(GeneratorTestSupport.policyWithConfig("edge_limiting", Map.of(
                "fixed_window_limiters", List.of(
                        Map.of("count", 100, "window", 60, "key", Map.of("name", "user"))))));
        var ctx = GeneratorTestSupport.context(service);

        rateLimitPolicyGenerator.bindManual(RateLimitSupport.forManual());
        String fromGenerator = rateLimitPolicyGenerator.generate(ctx);
        Map<String, Object> fromGeneratorMap = YamlAssertions.parse(fromGenerator);
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(rateLimitPolicyManifest(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGeneratorMap, fromRecord);
    }

    @Test
    void rateLimitPolicy_leakyBucket_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Rate API", "rate-api");
        service.policies = List.of(GeneratorTestSupport.policyWithConfig("edge_limiting", Map.of(
                "leaky_bucket_limiters", List.of(
                        Map.of("rate", 50, "key", Map.of("name", "user"))))));
        var ctx = GeneratorTestSupport.context(service);

        rateLimitPolicyGenerator.bindManual(RateLimitSupport.forManual());
        Map<String, Object> fromGenerator = YamlAssertions.parse(rateLimitPolicyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(
                rateLimitPolicyManifestLeaky(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    @Test
    void rateLimitPolicy_planCeiling_recordMatchesGeneratorOutput() {
        var service = GeneratorTestSupport.basicService("Rate API", "rate-api");
        service.applicationPlans = List.of(planWithMinuteLimit(42));
        var ctx = GeneratorTestSupport.context(service);

        rateLimitPolicyGenerator.bindManual(RateLimitSupport.forManual());
        Map<String, Object> fromGenerator = YamlAssertions.parse(rateLimitPolicyGenerator.generate(ctx));
        Map<String, Object> fromRecord = YamlAssertions.parse(serializer.toYaml(
                rateLimitPolicyManifestPlanCeiling(ctx)));

        MapEquivalenceSupport.assertEquivalent(fromGenerator, fromRecord);
    }

    private static String generateAuthPolicy(
            ConversionContext ctx, com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyContributor contributor) {
        AuthPolicyGenerator generator = new AuthPolicyGenerator();
        generator.bindManualContributors(List.of(contributor));
        return generator.generate(ctx);
    }

    private static TlsPolicyManifest tlsPolicyManifest(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String issuerKind = ctx.options.tlsIssuerKind;
        String issuerName = ctx.options.tlsIssuerName;
        String kind = issuerKind != null && !issuerKind.isBlank() ? issuerKind : "ClusterIssuer";
        String issuer = issuerName != null && !issuerName.isBlank() ? issuerName : "letsencrypt-prod";
        return new TlsPolicyManifest(
                "kuadrant.io/v1",
                "TLSPolicy",
                kuadrantMeta(name + "-tls-policy", name),
                new TlsPolicySpec(
                        gatewayTarget(name),
                        new IssuerRef("cert-manager.io", kind, issuer)));
    }

    private static DnsPolicyManifest dnsPolicyManifest(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String providerSecret = ctx.options.dnsProviderSecretName;
        List<ProviderRef> providerRefs = providerSecret != null && !providerSecret.isBlank()
                ? List.of(new ProviderRef(providerSecret.trim()))
                : null;
        return new DnsPolicyManifest(
                "kuadrant.io/v1",
                "DNSPolicy",
                kuadrantMeta(name + "-dns-policy", name),
                new DnsPolicySpec(gatewayTarget(name), providerRefs));
    }

    private static ApiProductManifest apiProductManifest(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String displayName = ctx.service.name != null ? ctx.service.name : name;
        String description = ctx.service.description != null ? ctx.service.description : "Migrated from 3scale";
        return new ApiProductManifest(
                "devportal.kuadrant.io/v1alpha1",
                "APIProduct",
                kuadrantMeta(name, name),
                new ApiProductSpec(
                        displayName,
                        description,
                        "automatic",
                        "Published",
                        httpRouteTarget(name),
                        "v1"));
    }

    private static ApiKeyManifest apiKeyManifest(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        return new ApiKeyManifest(
                "devportal.kuadrant.io/v1alpha1",
                "APIKey",
                kuadrantMeta(name + "-api-key", name),
                new ApiKeySpec(
                        new ApiProductRef(name),
                        "basic",
                        new RequestedBy("admin@example.com", "admin"),
                        new SecretRef(name + "-api-key")));
    }

    private static AuthPolicyManifest authPolicyManifest(ConversionContext ctx, Authentication auth) {
        String name = ctx.serviceKebabName;
        Map<String, AuthenticationRule> authentication;
        if (auth != null && "jwt".equals(auth.type)) {
            String issuer = auth.oidcIssuerEndpoint != null && !auth.oidcIssuerEndpoint.isBlank()
                    ? auth.oidcIssuerEndpoint
                    : ConversionConstants.DEFAULT_OIDC_ISSUER_URL;
            authentication = Map.of(
                    "jwt-auth",
                    new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", issuer))));
        } else {
            authentication = Map.of();
        }
        return new AuthPolicyManifest(
                "kuadrant.io/v1",
                "AuthPolicy",
                authMeta(name),
                new AuthPolicySpec(
                        httpRouteTarget(name),
                        new AuthPolicyRules(authentication, null, null)));
    }

    private static RateLimitPolicyManifest rateLimitPolicyManifest(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        Map<String, LimitDefinition> limits = new LinkedHashMap<>();
        limits.put("user_1", new LimitDefinition(List.of(new Rate(100, "60s"))));
        return new RateLimitPolicyManifest(
                "kuadrant.io/v1",
                "RateLimitPolicy",
                kuadrantMeta(name + "-ratelimit", name),
                new RateLimitPolicySpec(httpRouteTarget(name), limits));
    }

    private static RateLimitPolicyManifest rateLimitPolicyManifestLeaky(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        Map<String, LimitDefinition> limits = new LinkedHashMap<>();
        limits.put("user_1", new LimitDefinition(List.of(new Rate(50, "1s"))));
        return new RateLimitPolicyManifest(
                "kuadrant.io/v1",
                "RateLimitPolicy",
                kuadrantMeta(name + "-ratelimit", name),
                new RateLimitPolicySpec(httpRouteTarget(name), limits));
    }

    private static RateLimitPolicyManifest rateLimitPolicyManifestPlanCeiling(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        Map<String, LimitDefinition> limits = new LinkedHashMap<>();
        limits.put("global", new LimitDefinition(List.of(new Rate(42, "60s"))));
        return new RateLimitPolicyManifest(
                "kuadrant.io/v1",
                "RateLimitPolicy",
                kuadrantMeta(name + "-ratelimit", name),
                new RateLimitPolicySpec(httpRouteTarget(name), limits));
    }

    private static ApplicationPlan planWithMinuteLimit(int value) {
        ApplicationPlan plan = new ApplicationPlan();
        plan.limits = List.of(Map.of("period", "minute", "value", value));
        return plan;
    }

    private static ManifestMeta authMeta(String appLabel) {
        return new ManifestMeta(
                appLabel + "-auth",
                GeneratorTestSupport.NAMESPACE,
                Map.of("app", appLabel, "migrated-from", "3scale"),
                null);
    }

    private static ManifestMeta kuadrantMeta(String resourceName, String appLabel) {
        return new ManifestMeta(
                resourceName,
                GeneratorTestSupport.NAMESPACE,
                Map.of("app", appLabel, "migrated-from", "3scale"),
                null);
    }

    private static TargetRef gatewayTarget(String name) {
        return new TargetRef("gateway.networking.k8s.io", "Gateway", name + "-gateway");
    }

    private static TargetRef httpRouteTarget(String name) {
        return new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route");
    }
}
