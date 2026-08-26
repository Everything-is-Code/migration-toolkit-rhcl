package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteAnnotationsContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(0, 30, 0));
        service.policies.add(ContributorTestFixtures.policy("content_limits", true,
                Map.of("response", 8192)));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new HttpRouteAnnotationsContributor().contribute(builder, ctx);

        assertTrue(builder.build().contains("3scale-migration/upstream-send-timeout"));
        assertTrue(builder.build().contains("3scale-migration/response-content-limit"));
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new HttpRouteAnnotationsContributor().contribute(builder, ctx);

        assertFalse(builder.build().contains("3scale-migration/"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(0, 45, 0));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new HttpRouteAnnotationsContributor().contribute(builder, ctx);

        assertTrue(builder.build().contains("3scale-migration/upstream-send-timeout: \"45s\""));
    }
}
