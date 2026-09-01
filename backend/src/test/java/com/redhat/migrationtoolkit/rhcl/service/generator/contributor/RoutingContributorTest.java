package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport.MatchKind;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport.MatchOp;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetryBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeoutsBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingContributorTest {

    @Test
    void contribute_headerExact_emitsMatchAndOverrideBackend() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/fallback"));
        service.policies.add(routingPolicy(List.of(rule(
                "https://override.example.com",
                "and",
                List.of(op("header", "Test-Header", "==", "special"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        int backendsBefore = ctx.resolvedBackends.size();
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("headers:") || yaml.contains("Test-Header"), yaml);
        assertTrue(yaml.contains("Test-Header"), yaml);
        assertTrue(yaml.contains("special"), yaml);
        assertTrue(yaml.contains("override.example.com") || yaml.contains("hostname:"), yaml);
        assertEquals(backendsBefore, ctx.resolvedBackends.size(),
                "Must not mutate ctx.resolvedBackends for ephemeral override");
    }

    @Test
    void contribute_queryAndPath_emitMatches() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://other.example.com",
                "and",
                List.of(
                        op("query_arg", "env", "==", "staging"),
                        op("path", null, "==", "/special"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("queryParams:") || yaml.contains("query_params:"), yaml);
        assertTrue(yaml.contains("env"), yaml);
        assertTrue(yaml.contains("/special"), yaml);
        assertTrue(yaml.contains("PathPrefix"), yaml);
        assertTrue(builder.pathsForOptions().contains("/special"),
                "Path ops must register addPathForOptions");
    }

    @Test
    void contribute_combineOpAnd_oneRuleWithBothMatches() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://and.example.com",
                "and",
                List.of(
                        op("header", "X-Tenant", "==", "acme"),
                        op("path", null, "==", "/v1"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        // Count rules by "- backendRefs:" occurrences (Fabric8 YAML structure)
        assertEquals(1, countOccurrences(yaml, "- backendRefs:"));
        assertTrue(yaml.contains("X-Tenant"), yaml);
        assertTrue(yaml.contains("/v1"), yaml);
    }

    @Test
    void contribute_combineOpOr_emitsSeparateRules() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://or.example.com",
                "or",
                List.of(
                        op("header", "X-Tenant", "==", "acme"),
                        op("path", null, "==", "/v2"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        // Count rules by "- backendRefs:" occurrences (Fabric8 YAML structure)
        assertEquals(2, countOccurrences(yaml, "- backendRefs:"));
        assertTrue(yaml.contains("X-Tenant"), yaml);
        assertTrue(yaml.contains("/v2"), yaml);
    }

    @Test
    void contribute_missingCombineOp_defaultsToAnd() {
        ApiService service = ContributorTestFixtures.apiService();
        Map<String, Object> condition = new HashMap<>();
        condition.put("operations", List.of(
                op("header", "X-A", "==", "1"),
                op("header", "X-B", "==", "2")));
        Map<String, Object> rule = new HashMap<>();
        rule.put("url", "https://default-and.example.com");
        rule.put("condition", condition);
        service.policies.add(routingPolicy(List.of(rule)));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        // Count rules by "- backendRefs:" occurrences (Fabric8 YAML structure)
        assertEquals(1, countOccurrences(yaml, "- backendRefs:"));
        assertTrue(yaml.contains("X-A") && yaml.contains("X-B"), yaml);
    }

    @Test
    void contribute_mixedJwtAndHeader_emitsHeaderOnly() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://jwt-mix.example.com",
                "and",
                List.of(
                        jwtOp("role", "==", "admin"),
                        op("header", "X-Route", "==", "yes"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("X-Route"), yaml);
        assertFalse(yaml.contains("jwt_claim") || yaml.contains("jwt-claim"), yaml);
        assertFalse(yaml.contains("name: role") || yaml.contains("admin"), yaml);
    }

    @Test
    void contribute_jwtOnly_emitsNoClaimMatch() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://jwt-only.example.com",
                "and",
                List.of(jwtOp("sub", "==", "user-1"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertFalse(yaml.contains("jwt-only.example.com"), yaml);
        assertFalse(yaml.contains("sub"), yaml);
        assertEquals(0, countOccurrences(yaml, "- matches:"));
    }

    @Test
    void contribute_productBackendHost_reusesResolvedBackend() {
        ApiService service = ContributorTestFixtures.apiService();
        // default fixture backend: http://api.example.com:8080
        service.policies.add(routingPolicy(List.of(rule(
                "http://api.example.com:8080",
                "and",
                List.of(op("header", "X-Reuse", "==", "1"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        List<ResolvedBackend> snapshot = new ArrayList<>(ctx.resolvedBackends);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("name: " + snapshot.get(0).refName)
                || yaml.contains(snapshot.get(0).refName), yaml);
        assertEquals(snapshot.size(), ctx.resolvedBackends.size());
        assertTrue(ctx.resolvedBackends.containsAll(snapshot));
    }

    @Test
    void contribute_ephemeralOverride_doesNotMutateResolvedBackends() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://ephemeral.example.com",
                "and",
                List.of(op("path", null, "==", "/epi"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        List<String> hostsBefore = ctx.resolvedBackends.stream()
                .map(b -> b.externalHost)
                .toList();
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("ephemeral.example.com") || yaml.contains("hostname:"), yaml);
        List<String> hostsAfter = ctx.resolvedBackends.stream()
                .map(b -> b.externalHost)
                .toList();
        assertEquals(hostsBefore, hostsAfter);
        assertFalse(hostsAfter.contains("ephemeral.example.com"));
    }

    @Test
    void contribute_unsupportedOp_skipsMatch() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://skip-op.example.com",
                "and",
                List.of(
                        op("header", "X-Ok", "==", "yes"),
                        op("header", "X-No", "!=", "no"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("X-Ok"), yaml);
        assertFalse(yaml.contains("X-No"), yaml);
    }

    // ── buildMatch unit tests ─────────────────────────────────────────────────

    @Test
    void buildMatch_headerOp_setsHeaderMatch() {
        List<MatchOp> ops = List.of(new MatchOp(MatchKind.HEADER, "X-Test", "==", "val"));
        HTTPRouteMatch match = RoutingContributor.buildMatch(ops);
        assertNotNull(match);
        assertFalse(match.getHeaders().isEmpty());
        assertEquals("X-Test", match.getHeaders().get(0).getName());
        assertEquals("val", match.getHeaders().get(0).getValue());
    }

    @Test
    void buildMatch_allUnsupported_returnsNull() {
        List<MatchOp> ops = List.of(new MatchOp(MatchKind.JWT_CLAIM, "claim", "==", "x"));
        HTTPRouteMatch match = RoutingContributor.buildMatch(ops);
        assertNull(match);
    }

    @Test
    void buildMatch_headerWithoutName_returnsNull() {
        List<MatchOp> ops = List.of(new MatchOp(MatchKind.HEADER, null, "==", "val"));
        assertNull(RoutingContributor.buildMatch(ops));
    }

    @Test
    void contribute_emptyEffectiveBackends_skipsRoutingRules() {
        ApiService service = ContributorTestFixtures.apiService();
        Map<String, Object> condition = new HashMap<>();
        condition.put("combine_op", "and");
        condition.put("operations", List.of(op("header", "X-Route", "==", "yes")));
        Map<String, Object> rule = new HashMap<>();
        rule.put("condition", condition);
        service.policies.add(routingPolicy(List.of(rule)));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setOverrideBackends(List.of());

        new RoutingContributor().contribute(builder, ctx);

        assertFalse(builder.build().contains("X-Route"));
    }

    @Test
    void contribute_withTimeoutsAndRetry() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://timed.example.com",
                "and",
                List.of(op("header", "X-Timed", "==", "1"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setTimeouts(new HTTPRouteTimeoutsBuilder().withRequest("8s").build());
        builder.setRetry(new HTTPRouteRetryBuilder().withAttempts(4).build());

        new RoutingContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("8s"), yaml);
        assertTrue(yaml.contains("attempts: 4") || yaml.contains("attempts:4"), yaml);
    }

    @Test
    void contribute_headerOpWithoutName_skipsRule() {
        ApiService service = ContributorTestFixtures.apiService();
        Map<String, Object> condition = new HashMap<>();
        condition.put("combine_op", "and");
        condition.put("operations", List.of(Map.of(
                "match", "header",
                "op", "==",
                "value", "orphan")));
        Map<String, Object> rule = new HashMap<>();
        rule.put("condition", condition);
        service.policies.add(routingPolicy(List.of(rule)));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RoutingContributor().contribute(builder, ctx);

        assertFalse(builder.build().contains("orphan"));
    }

    private static Policy routingPolicy(List<Map<String, Object>> rules) {
        Policy policy = new Policy();
        policy.name = "routing";
        policy.enabled = true;
        policy.configuration = new HashMap<>();
        policy.configuration.put("rules", rules);
        return policy;
    }

    private static Map<String, Object> rule(String url, String combineOp, List<Map<String, Object>> ops) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("combine_op", combineOp);
        condition.put("operations", ops);
        Map<String, Object> rule = new HashMap<>();
        rule.put("url", url);
        rule.put("condition", condition);
        return rule;
    }

    private static Map<String, Object> op(String match, String name, String op, String value) {
        Map<String, Object> operation = new HashMap<>();
        operation.put("match", match);
        operation.put("op", op);
        operation.put("value", value);
        if ("header".equals(match)) {
            operation.put("header_name", name);
        } else if ("query_arg".equals(match)) {
            operation.put("query_arg_name", name);
        }
        return operation;
    }

    private static Map<String, Object> jwtOp(String claim, String op, String value) {
        Map<String, Object> operation = new HashMap<>();
        operation.put("match", "jwt_claim");
        operation.put("jwt_claim_name", claim);
        operation.put("op", op);
        operation.put("value", value);
        return operation;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
