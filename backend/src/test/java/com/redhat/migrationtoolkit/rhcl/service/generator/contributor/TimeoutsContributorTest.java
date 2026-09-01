package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeoutsContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(5, 0, 30));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new TimeoutsContributor().contribute(builder, ctx);

        assertNotNull(builder.timeouts());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new TimeoutsContributor().contribute(builder, ctx);

        assertNull(builder.timeouts());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(10, 20, 40));
        HTTPRouteTimeouts timeouts = TimeoutsContributor.buildTimeouts(service);

        assertNotNull(timeouts);
        assertEquals("10s", timeouts.getBackendRequest());
        assertEquals("40s", timeouts.getRequest());
        // send_timeout (20s) has no Gateway API field — recorded as annotation by
        // HttpRouteAnnotationsContributor instead
    }

    @Test
    void buildTimeouts_noValues_returnsNull() {
        ApiService service = ContributorTestFixtures.apiService();
        assertNull(TimeoutsContributor.buildTimeouts(service));
    }

    @Test
    void buildTimeouts_onlyReadTimeout_sets_request() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.upstreamConnectionPolicy(0, 0, 15));
        HTTPRouteTimeouts timeouts = TimeoutsContributor.buildTimeouts(service);

        assertNotNull(timeouts);
        assertEquals("15s", timeouts.getRequest());
        assertNull(timeouts.getBackendRequest());
    }
}
