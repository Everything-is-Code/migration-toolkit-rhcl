package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamContributorTest {

    @Test
    void contribute_globalOverride_setsBackendsAndHostRewrite() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/users"));
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(Map.of("regex", ".*", "url", "https://override.example.com")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);
        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("override.example.com") || yaml.contains("name: demo-api-backend"));
        assertTrue(yaml.contains("URLRewrite") || yaml.contains("urlRewrite"));
        assertTrue(yaml.contains("hostname:"));
        assertTrue(builder.effectiveBackends() != builder.backends());
    }

    @Test
    void contribute_pathScoped_prependsTwoRules() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/fallback"));
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com"),
                        Map.of("regex", "^/v2/.*", "url", "https://v2.example.com")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);
        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        int v1 = yaml.indexOf("v1.example.com");
        int v2 = yaml.indexOf("v2.example.com");
        int fallback = yaml.indexOf("value: \"/fallback\"");
        assertTrue(v1 >= 0 && v2 >= 0 && fallback >= 0);
        assertTrue(v1 < fallback && v2 < fallback);
        assertTrue(yaml.contains("type: PathPrefix") || yaml.contains("type: RegularExpression"));
    }

    @Test
    void contribute_mixedApprox_emitsOnlyConvertible() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/fallback"));
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/ok", "url", "https://ok.example.com"),
                        Map.of("regex", "^/api(?=!)", "url", "https://skip.example.com")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("ok.example.com") || yaml.contains("value: \"/ok\""));
        assertFalse(yaml.contains("skip.example.com"));
    }

    @Test
    void contribute_pathScoped_regexWithQuotes_usesYamlDoubleQuoted() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/fallback"));
        // Complex regex (not a simple PathPrefix) containing a double quote
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/api/\"quoted\".*", "url", "https://quoted.example.com")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("type: RegularExpression"));
        assertTrue(yaml.contains("value: \"^/api/\\\"quoted\\\".*\""));
        assertTrue(yaml.contains("quoted.example.com"));
    }

    @Test
    void contribute_globalTopLevelUrl_setsOverrideBackends() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(upstreamPolicy(Map.of(
                "url", "http://upstream-svc:8080",
                "rules", List.of())));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);

        assertEquals(1, builder.effectiveBackends().size());
        assertTrue(builder.effectiveBackends().get(0).privateEndpoint.contains("upstream-svc")
                || builder.build().contains("upstream-svc"));
    }

    @Test
    void contribute_pathScoped_pathPrefix_registersOptionsPath() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "https://v1.example.com")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new UpstreamContributor().contribute(builder, ctx);

        assertTrue(builder.pathsForOptions().contains("/v1"));
    }

    @Test
    void contribute_noUpstreamPolicy_isNoOp() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        List<ResolvedBackend> before = List.copyOf(builder.backends());

        new UpstreamContributor().contribute(builder, ctx);

        assertEquals(before, builder.backends());
        assertTrue(builder.build().isBlank() || !builder.build().contains("backendRefs"));
    }

    private static Policy upstreamPolicy(Map<String, Object> configuration) {
        Policy policy = new Policy();
        policy.name = "upstream";
        policy.enabled = true;
        policy.configuration = new HashMap<>(configuration);
        return policy;
    }
}
