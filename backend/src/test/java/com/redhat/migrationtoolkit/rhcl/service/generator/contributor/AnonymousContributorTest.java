package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

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
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(policy);
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);
        new AnonymousContributor().contribute(builder, ctx);

        String yaml = SERIALIZER.toYaml(builder.build());
        assertTrue(yaml.contains("x-user-key:"));
        assertTrue(yaml.contains("3scale-migration/anonymous-access"));
    }

    @Test
    void contribute_skipsWhenBaseAlreadySet() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.anonymousAccessPolicy("user_key", Map.of()));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);
        // Pre-populate with a different auth rule
        builder.addAuthentication("pre-existing",
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule(Map.of("existing", Map.of())));

        new AnonymousContributor().contribute(builder, ctx);

        AuthPolicyManifest manifest = builder.build();
        // Should still have the pre-existing rule and not add anonymous
        assertTrue(manifest.spec().rules().authentication().containsKey("pre-existing"));
        assertFalse(manifest.spec().rules().authentication().containsKey("anonymous"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.anonymousAccessPolicy(
                "app_id_and_app_key", Map.of("app_id", "aid", "app_key", "akey"));
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(policy);
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);
        new AnonymousContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("anonymous:"));
        assertTrue(yaml.contains("3scale-migration/anonymous-access"));
        assertTrue(yaml.contains("x-app-id:"));
        assertTrue(yaml.contains("value: aid"));
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

        String yaml = SERIALIZER.toYaml(builder.build());
        assertTrue(yaml.contains("kind: Gateway"));
        assertTrue(yaml.contains("name: demo-api-gateway"));
    }
}
