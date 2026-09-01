package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyAuthenticationContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new EmptyAuthenticationContributor().contribute(builder, ctx);

        assertTrue(builder.hasBase());
    }

    @Test
    void shouldContribute_false_whenBaseAlreadySet() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);
        // Pre-set a real authentication rule
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://idp.example.com"))));

        new EmptyAuthenticationContributor().contribute(builder, ctx);

        // Should still have the jwt-auth rule, not empty
        assertTrue(builder.build().spec().rules().authentication().containsKey("jwt-auth"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new EmptyAuthenticationContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("authentication: {}") || yaml.contains("authentication:\n"),
                "authentication should be present");
        assertTrue(yaml.contains("name: demo-api-auth"));
        assertTrue(yaml.contains("kind: HTTPRoute"));
    }
}
