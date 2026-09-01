package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) parsed.get("stringData");

        assertEquals("demo-api-anonymous-credentials", metadata.get("name"));
        assertEquals("aid", stringData.get("app_id"));
        assertEquals("akey", stringData.get("app_key"));
    }
}
