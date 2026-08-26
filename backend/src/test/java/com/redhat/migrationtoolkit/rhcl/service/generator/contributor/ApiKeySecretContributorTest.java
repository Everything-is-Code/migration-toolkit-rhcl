package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeySecretContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new ApiKeySecretContributor().contribute(builder, ctx);

        assertTrue(builder.hasSecret());
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new ApiKeySecretContributor().contribute(builder, ctx);

        assertFalse(builder.hasSecret());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new ApiKeySecretContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("name: demo-api-api-key"));
        assertTrue(yaml.contains("api_key:"));
    }
}
