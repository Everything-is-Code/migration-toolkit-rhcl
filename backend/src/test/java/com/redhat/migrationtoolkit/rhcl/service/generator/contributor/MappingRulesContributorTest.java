package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetryBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeoutsBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingRulesContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/users"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new MappingRulesContributor().contribute(builder, ctx);

        assertTrue(builder.build().contains("/users"));
    }

    @Test
    void shouldContribute_false_whenNoRules_usesCatchAll() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        String before = builder.build();

        new MappingRulesContributor().contribute(builder, ctx);
        String after = builder.build();

        assertFalse(before.contains("backendRefs:"));
        // Fabric8 serializes path values with double quotes; check substring
        assertTrue(after.contains("\"/\"") || after.contains("value: /"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("POST", "/items"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        // Fabric8 serializes string values with double quotes; check value substrings
        assertTrue(yaml.contains("POST"), yaml);
        assertTrue(yaml.contains("backendRefs:"), yaml);
        assertTrue(builder.pathsForOptions().contains("/items"));
    }

    @Test
    void contribute_usesOverrideBackendsWhenSet() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/users"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setOverrideBackends(List.of(new ResolvedBackend(
                BackendType.EXTERNAL, "upstream-override", "se", "dr",
                "override.example.com", 443, true, "/", null, "https://override.example.com")));

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        // Fabric8 serializes name values with double quotes; check value substrings
        assertTrue(yaml.contains("upstream-override"), yaml);
        assertTrue(yaml.contains("override.example.com"), yaml);
    }

    @Test
    void contribute_duplicatePathAndMethod_skipsSecondRule() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/dup"));
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/dup"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertEquals(1, countOccurrences(yaml, "- backendRefs:"));
    }

    @Test
    void contribute_catchAllRoot_registersCatchAllMethod() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/"));
        service.mappingRules.add(ContributorTestFixtures.mappingRule("POST", "/users"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new MappingRulesContributor().contribute(builder, ctx);

        assertTrue(builder.pathsForOptions().contains("/"));
        assertTrue(builder.pathsForOptions().contains("/users"));
    }

    @Test
    void contribute_withTimeoutsAndRetry_onMappingRules() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/timed"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setTimeouts(new HTTPRouteTimeoutsBuilder().withRequest("15s").build());
        builder.setRetry(new HTTPRouteRetryBuilder().withAttempts(2).build());

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("15s"), yaml);
        assertTrue(yaml.contains("attempts: 2") || yaml.contains("attempts:2"), yaml);
    }

    @Test
    void contribute_withTimeouts_onCatchAllWhenNoRules() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setTimeouts(new HTTPRouteTimeoutsBuilder().withRequest("30s").build());

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("30s"), yaml);
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
