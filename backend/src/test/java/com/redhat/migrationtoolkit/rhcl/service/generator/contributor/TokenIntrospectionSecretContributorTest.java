package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenIntrospectionSecretContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.tokenIntrospectionPolicy(
                "https://idp.example.com/introspect"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new TokenIntrospectionSecretContributor().contribute(builder, ctx);

        assertTrue(builder.hasSecret());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new TokenIntrospectionSecretContributor().contribute(builder, ctx);

        assertFalse(builder.hasSecret());
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.tokenIntrospectionPolicy(
                "https://idp.example.com/introspect");
        String yaml = TokenIntrospectionSecretContributor.generateTokenIntrospectionSecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy);

        assertTrue(yaml.contains("name: demo-api-oauth2-introspection"));
        assertTrue(yaml.contains("clientID: \"cid\""));
        assertTrue(yaml.contains("clientSecret: \"secret\""));
    }

    @Test
    void contribute_warning_whenCredentialsIncomplete() {
        Policy policy = ContributorTestFixtures.policy("token_introspection", true,
                java.util.Map.of("introspection_url", "https://idp.example.com/introspect"));
        String yaml = TokenIntrospectionSecretContributor.generateTokenIntrospectionSecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy);

        assertTrue(yaml.contains("WARNING: token_introspection credentials incomplete"));
        assertTrue(yaml.contains(ConversionConstants.CREDENTIAL_PLACEHOLDER));
    }
}
