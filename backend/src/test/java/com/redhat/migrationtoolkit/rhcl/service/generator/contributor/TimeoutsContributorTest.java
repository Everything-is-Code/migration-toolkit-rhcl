package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutsContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(5, 0, 30));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new TimeoutsContributor().contribute(builder, ctx);

        assertFalse(builder.timeoutsBlock().isEmpty());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new TimeoutsContributor().contribute(builder, ctx);

        assertTrue(builder.timeoutsBlock().isEmpty());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(10, 20, 40));
        String block = TimeoutsContributor.buildTimeoutsBlock(service);

        assertTrue(block.contains("timeouts:"));
        assertTrue(block.contains("backendRequest: \"10s\""));
        assertTrue(block.contains("request: \"40s\""));
        assertTrue(block.contains("send_timeout: 20s"));
    }
}
