package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetryContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.retryPolicy(3));
        ConversionOptions options = new ConversionOptions();
        options.retriesSupported = true;
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RetryContributor().contribute(builder, ctx);

        assertNotNull(builder.retry());
        assertEquals(3, builder.retry().getAttempts());
    }

    @Test
    void shouldContribute_false_whenRetriesUnsupported() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.retryPolicy(3));
        ConversionOptions options = new ConversionOptions();
        options.retriesSupported = false;
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RetryContributor().contribute(builder, ctx);

        assertNull(builder.retry());
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.retryPolicy(5));
        ConversionOptions options = new ConversionOptions();
        options.retriesSupported = true;
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new RetryContributor().contribute(builder, ctx);

        assertNotNull(builder.retry());
        assertEquals(5, builder.retry().getAttempts());
    }
}
