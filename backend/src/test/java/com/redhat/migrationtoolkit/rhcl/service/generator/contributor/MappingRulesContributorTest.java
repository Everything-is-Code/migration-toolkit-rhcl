package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

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

        assertTrue(builder.build().contains("value: \"/users\""));
    }

    @Test
    void shouldContribute_false_whenNoRules_usesCatchAll() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        String before = builder.build();

        new MappingRulesContributor().contribute(builder, ctx);
        String after = builder.build();

        assertFalse(before.contains("backendRefs:"));
        assertTrue(after.contains("value: \"/\""));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("POST", "/items"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new MappingRulesContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("method: POST"));
        assertTrue(yaml.contains("backendRefs:"));
        assertTrue(builder.pathsForOptions().contains("/items"));
    }
}
