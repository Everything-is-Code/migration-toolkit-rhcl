package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new JwtAuthenticationContributor().contribute(builder, ctx);

        assertTrue(builder.hasBase());
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new JwtAuthenticationContributor().contribute(builder, ctx);

        assertFalse(builder.hasBase());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new JwtAuthenticationContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("jwt-auth:"));
        assertTrue(yaml.contains("issuerUrl: https://idp.example.com/realms/demo"));
    }

    @Test
    void contribute_defaultIssuer_whenMissing() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.authentication.oidcIssuerEndpoint = null;
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new JwtAuthenticationContributor().contribute(builder, ctx);

        assertTrue(SERIALIZER.toYaml(builder.build()).contains(ConversionConstants.DEFAULT_OIDC_ISSUER_URL));
    }
}
