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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingSupportTest {

    @Test
    void parseOp_headerQueryPathJwt_setsKindsAndFields() {
        RoutingSupport.MatchOp header = RoutingSupport.parseOp(Map.of(
                "match", "header", "header_name", "X-Tenant", "op", "==", "value", "acme"));
        assertEquals(RoutingSupport.MatchKind.HEADER, header.kind());
        assertEquals("X-Tenant", header.name());
        assertEquals("==", header.op());
        assertEquals("acme", header.value());
        assertTrue(header.convertible());

        RoutingSupport.MatchOp query = RoutingSupport.parseOp(Map.of(
                "match", "query_arg", "query_arg_name", "env", "op", "==", "value", "staging"));
        assertEquals(RoutingSupport.MatchKind.QUERY_ARG, query.kind());
        assertEquals("env", query.name());
        assertTrue(query.convertible());

        RoutingSupport.MatchOp path = RoutingSupport.parseOp(Map.of(
                "match", "path", "op", "==", "value", "/special"));
        assertEquals(RoutingSupport.MatchKind.PATH, path.kind());
        assertTrue(path.convertible());

        RoutingSupport.MatchOp jwt = RoutingSupport.parseOp(Map.of(
                "match", "jwt_claim", "jwt_claim_name", "role", "op", "==", "value", "admin"));
        assertEquals(RoutingSupport.MatchKind.JWT_CLAIM, jwt.kind());
        assertEquals("role", jwt.name());
        assertFalse(jwt.convertible());
    }

    @Test
    void matchOp_nonEquality_notConvertible() {
        RoutingSupport.MatchOp op = RoutingSupport.parseOp(Map.of(
                "match", "header", "header_name", "X-A", "op", "!=", "value", "1"));
        assertFalse(op.convertible());
        assertFalse(RoutingSupport.isEqualityOp("!="));
        assertTrue(RoutingSupport.isEqualityOp("=="));
    }

    @Test
    void hasJwtClaimOperations_trueWhenJwtPresent() {
        Policy routing = routingPolicy(List.of(rule(
                "https://jwt.example.com",
                "and",
                List.of(
                        Map.of("match", "header", "header_name", "X-A", "op", "==", "value", "1"),
                        Map.of("match", "jwt_claim", "jwt_claim_name", "sub", "op", "==", "value", "u1")))));
        assertTrue(RoutingSupport.hasJwtClaimOperations(routing));
    }

    @Test
    void resolveBackendForUrl_prefersHostMatchingProductBackend() {
        ApiService service = ContributorTestFixtures.apiService();
        // Fixture backend: http://api.example.com:8080
        ConversionContext ctx = ContributorTestFixtures.context(service);
        ResolvedBackend matched = RoutingSupport.resolveBackendForUrl(ctx, "http://api.example.com:8080/v2");
        assertNotNull(matched);
        assertSame(ctx.resolvedBackends.get(0), matched);

        ResolvedBackend ephemeral = RoutingSupport.resolveBackendForUrl(ctx, "https://other.example.com");
        assertNotNull(ephemeral);
        assertEquals(BackendType.EXTERNAL, ephemeral.type);
        assertEquals("other.example.com", ephemeral.externalHost);
        assertFalse(ctx.resolvedBackends.contains(ephemeral));
    }

    @Test
    void isUnmatchedExternalOverride_trueForUnknownExternalHost() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        assertTrue(RoutingSupport.isUnmatchedExternalOverride(ctx, "https://unknown.example.com"));
        assertFalse(RoutingSupport.isUnmatchedExternalOverride(ctx, "http://api.example.com:8080"));
        assertFalse(RoutingSupport.isUnmatchedExternalOverride(ctx, "http://incluster-svc:8080"));
    }

    @Test
    void buildReadmeNotes_gapForUnsupportedOpAndJwt() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(routingPolicy(List.of(rule(
                "https://gap.example.com",
                "and",
                List.of(
                        Map.of("match", "header", "header_name", "X-A", "op", "!=", "value", "1"),
                        Map.of("match", "jwt_claim", "jwt_claim_name", "role", "op", "==", "value", "admin"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);

        String notes = RoutingSupport.buildReadmeNotes(service, new PolicyFinder(), ctx);

        assertTrue(notes.contains("## WARNING: Routing conversion gaps"));
        assertTrue(notes.contains("unsupported routing op"));
        assertTrue(notes.contains("jwt_claim"));
        assertTrue(notes.contains("ServiceEntry") || notes.contains("gap.example.com"));
    }

    @Test
    void buildReadmeNotes_happyPath_whenNoGaps() {
        ApiService service = ContributorTestFixtures.apiService();
        // Matching product host + equality-only ops → no gap bullets
        service.policies.add(routingPolicy(List.of(rule(
                "http://api.example.com:8080",
                "and",
                List.of(Map.of("match", "header", "header_name", "X-Tenant", "op", "==", "value", "acme"))))));
        ConversionContext ctx = ContributorTestFixtures.context(service);

        String notes = RoutingSupport.buildReadmeNotes(service, new PolicyFinder(), ctx);

        assertTrue(notes.contains("## Routing"));
        assertFalse(notes.contains("## WARNING"));
        assertTrue(notes.contains("httproute.yaml"));
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
}
