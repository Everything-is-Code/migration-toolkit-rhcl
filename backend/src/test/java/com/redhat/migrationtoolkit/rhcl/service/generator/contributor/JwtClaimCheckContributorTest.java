package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtClaimCheckContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.policies.add(ContributorTestFixtures.jwtClaimCheckPolicy(List.of(
                Map.of("jwt_claim", "sub", "op", "==", "value", "user-a"))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new JwtClaimCheckContributor().contribute(builder, ctx);

        assertTrue(builder.build().spec().rules().authorization().containsKey("jwt-claim-check"));
    }

    @Test
    void shouldContribute_false_withoutBase() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.policies.add(ContributorTestFixtures.jwtClaimCheckPolicy(List.of(
                Map.of("jwt_claim", "sub", "op", "==", "value", "user-a"))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new JwtClaimCheckContributor().contribute(builder, ctx);

        assertFalse(builder.hasBase());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.policies.add(ContributorTestFixtures.jwtClaimCheckPolicy(List.of(
                Map.of("jwt_claim", "sub", "op", "==", "value", "alice"))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new JwtClaimCheckContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("auth.identity.sub"));
        assertTrue(yaml.contains("value: alice"));
    }
}
