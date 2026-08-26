package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Oauth2IntrospectionContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.tokenIntrospectionPolicy(
                "https://idp.example.com/introspect"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new Oauth2IntrospectionContributor().contribute(builder, ctx);

        assertTrue(builder.hasBase());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new Oauth2IntrospectionContributor().contribute(builder, ctx);

        assertFalse(builder.hasBase());
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.policy("token_introspection", true, new java.util.HashMap<>(Map.of(
                "introspection_url", "https://idp.example.com/introspect",
                "client_id", "cid",
                "client_secret", "secret",
                "token_type_hint", "access_token")));
        String yaml = Oauth2IntrospectionContributor.generateOauth2IntrospectionAuthPolicy(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy, "");

        assertTrue(yaml.contains("oauth2-introspection:"));
        assertTrue(yaml.contains("endpoint: https://idp.example.com/introspect"));
        assertTrue(yaml.contains("tokenTypeHint: access_token"));
        assertTrue(yaml.contains("name: demo-api-oauth2-introspection"));
    }

    @Test
    void generate_returnsNull_whenEndpointMissing() {
        Policy policy = ContributorTestFixtures.policy("token_introspection", true, Map.of());
        assertNull(Oauth2IntrospectionContributor.generateOauth2IntrospectionAuthPolicy(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy, ""));
    }
}
