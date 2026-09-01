package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAuthenticationContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new ApiKeyAuthenticationContributor().contribute(builder, ctx);

        assertTrue(builder.hasBase());
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new ApiKeyAuthenticationContributor().contribute(builder, ctx);

        assertFalse(builder.hasBase());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new ApiKeyAuthenticationContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("api-key-auth:"));
        assertTrue(yaml.contains("prefix: APIKEY"));
        assertTrue(yaml.contains("name: demo-api-auth"));
    }
}
