package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousSecretContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.anonymousAccessPolicy(
                "user_key", Map.of("user_key", "secret-uk")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AnonymousSecretContributor().contribute(builder, ctx);

        assertTrue(builder.hasSecret());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AnonymousSecretContributor().contribute(builder, ctx);

        assertFalse(builder.hasSecret());
    }

    @Test
    void contribute_userKeyViaContributor() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.anonymousAccessPolicy(
                "user_key", Map.of("user_key", "uk-secret")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AnonymousSecretContributor().contribute(builder, ctx);

        assertTrue(builder.build().contains("user_key: \"uk-secret\""));
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.anonymousAccessPolicy(
                "app_id", Map.of("app_id", "aid", "app_key", "akey"));
        String yaml = AnonymousSecretContributor.buildAnonymousSecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy);

        assertTrue(yaml.contains("name: demo-api-anonymous-credentials"));
        assertTrue(yaml.contains("app_id: \"aid\""));
        assertTrue(yaml.contains("app_key: \"akey\""));
    }
}
