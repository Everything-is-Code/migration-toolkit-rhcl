package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtClaimCheckSupportTest {

    @Test
    void parseRules_eqNeqAndMatches_mapToAuthorinoOps() {
        Policy policy = ConversionSupportTestFixtures.policy("jwt_claim_check", true, Map.of(
                "rules", List.of(Map.of(
                        "operations", List.of(
                                Map.of("jwt_claim", "sub", "op", "==", "value", "user-a"),
                                Map.of("jwt_claim", "role", "op", "!=", "value", "admin"),
                                Map.of("jwt_claim", "scope", "op", "matches", "value", "read.*")
                        )))));

        JwtClaimCheckSupport.JwtClaimParseResult parsed = JwtClaimCheckSupport.parseRules(policy);

        assertEquals(3, parsed.patterns().size());
        assertEquals("eq", parsed.patterns().get(0).operator());
        assertEquals("neq", parsed.patterns().get(1).operator());
        assertEquals("matches", parsed.patterns().get(2).operator());
        assertEquals("auth.identity.sub", parsed.patterns().get(0).selector());
    }

    @Test
    void parseRules_orAndLiquid_addGapNotes() {
        Policy orPolicy = ConversionSupportTestFixtures.policy("jwt_claim_check", true, Map.of(
                "enable_extended_context", true,
                "rules", List.of(Map.of(
                        "combine_op", "or",
                        "operations", List.of(
                                Map.of("jwt_claim", "sub", "op", "==", "value", "x"))))));

        JwtClaimCheckSupport.JwtClaimParseResult orParsed = JwtClaimCheckSupport.parseRules(orPolicy);
        assertTrue(orParsed.patterns().isEmpty());
        assertTrue(orParsed.gapNotes().stream().anyMatch(n -> n.contains("combine_op=or")));
        assertTrue(orParsed.gapNotes().stream().anyMatch(n -> n.contains("enable_extended_context")));

        Policy liquidPolicy = ConversionSupportTestFixtures.policy("jwt_claim_check", true, Map.of(
                "rules", List.of(Map.of(
                        "resource_type", "liquid",
                        "operations", List.of(Map.of(
                                "jwt_claim_type", "liquid",
                                "value_type", "liquid",
                                "jwt_claim", "sub",
                                "op", "==",
                                "value", "x"))))));

        JwtClaimCheckSupport.JwtClaimParseResult liquidParsed = JwtClaimCheckSupport.parseRules(liquidPolicy);
        assertTrue(liquidParsed.patterns().isEmpty());
        assertTrue(liquidParsed.gapNotes().stream().anyMatch(n -> n.contains("resource_type=liquid")));
        assertTrue(liquidParsed.gapNotes().stream().anyMatch(n -> n.contains("liquid jwt_claim/value")));
    }

    @Test
    void buildNamedRule_emitsPatternMatchingBlock() {
        List<JwtClaimCheckSupport.JwtClaimPattern> patterns = List.of(
                new JwtClaimCheckSupport.JwtClaimPattern("auth.identity.sub", "eq", "user-a"));

        String yaml = JwtClaimCheckSupport.buildNamedRule(patterns);

        assertTrue(yaml.contains("jwt-claim-check:"));
        assertTrue(yaml.contains("selector: auth.identity.sub"));
        assertTrue(yaml.contains("operator: eq"));
        assertTrue(yaml.contains("value: \"user-a\""));
    }

    @Test
    void buildReadmeNotes_withGaps_includesWarningSection() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.policies.add(ConversionSupportTestFixtures.policy("jwt_claim_check", true, Map.of(
                "rules", List.of(Map.of(
                        "combine_op", "or",
                        "operations", List.of(
                                Map.of("jwt_claim", "sub", "op", "==", "value", "x")))))));

        String notes = JwtClaimCheckSupport.buildReadmeNotes(service, new PolicyFinder());

        assertTrue(notes.contains("## WARNING: JWT Claim Check conversion gaps"));
        assertTrue(notes.contains("combine_op=or"));
    }
}
