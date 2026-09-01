package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsOptionsContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("GET", "/api"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setCorsEnabled(true);
        builder.addPathForOptions("/api");

        new CorsOptionsContributor().contribute(builder, ctx);

        // Fabric8 serializes method values with double quotes
        assertTrue(builder.build().contains("OPTIONS"));
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.addPathForOptions("/api");

        new CorsOptionsContributor().contribute(builder, ctx);

        assertFalse(builder.build().contains("method: OPTIONS"));
    }

    @Test
    void contribute_skipsExistingOptionsMappingRule() {
        ApiService service = ContributorTestFixtures.apiService();
        service.mappingRules.add(ContributorTestFixtures.mappingRule("OPTIONS", "/dup"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setCorsEnabled(true);
        builder.addPathForOptions("/dup");

        new CorsOptionsContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertFalse(yaml.contains("method: OPTIONS"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.setCorsEnabled(true);
        builder.addPathForOptions("/api");
        builder.addPathForOptions("/v2");

        new CorsOptionsContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("/api"), yaml);
        assertTrue(yaml.contains("/v2"), yaml);
        // Fabric8 serializes method values with double quotes
        assertTrue(yaml.contains("OPTIONS"), yaml);
    }
}
