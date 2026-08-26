package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.anonymousAccessPolicy(
                "user_key", Map.of("user_key", "uk-123")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AnonymousContributor().contribute(builder, ctx);

        assertTrue(builder.hasBase());
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AnonymousContributor().contribute(builder, ctx);

        assertFalse(builder.hasBase());
    }

    @Test
    void contribute_userKeyAuthType() {
        Policy policy = ContributorTestFixtures.anonymousAccessPolicy("user_key", Map.of("user_key", "uk"));
        String yaml = AnonymousContributor.generateAnonymousAuthPolicy(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy, "httproute");
        assertTrue(yaml.contains("x-user-key:"));
        assertTrue(yaml.contains("auth-type: \"user_key\""));
    }

    @Test
    void contribute_skipsWhenBaseAlreadySet() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.anonymousAccessPolicy("user_key", Map.of()));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);
        builder.setBaseYaml("existing");

        new AnonymousContributor().contribute(builder, ctx);

        assertEquals("existing", builder.build());
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.anonymousAccessPolicy(
                "app_id_and_app_key", Map.of("app_id", "aid", "app_key", "akey"));
        String yaml = AnonymousContributor.generateAnonymousAuthPolicy(
                "demo-api", ContributorTestFixtures.NAMESPACE, policy, "httproute");

        assertTrue(yaml.contains("anonymous:"));
        assertTrue(yaml.contains("3scale-migration/anonymous-access: \"true\""));
        assertTrue(yaml.contains("x-app-id:"));
        assertTrue(yaml.contains("value: \"aid\""));
        assertTrue(yaml.contains("name: demo-api-route"));
    }

    @Test
    void contribute_gatewayTarget_usesGatewayRef() {
        Policy policy = ContributorTestFixtures.anonymousAccessPolicy("user_key", Map.of());
        ConversionOptions options = new ConversionOptions();
        options.anonymousTarget = "gateway";
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(policy);
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AnonymousContributor().contribute(builder, ctx);

        assertTrue(builder.build().contains("kind: Gateway"));
        assertTrue(builder.build().contains("name: demo-api-gateway"));
    }
}
