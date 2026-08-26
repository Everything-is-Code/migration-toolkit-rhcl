package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(builder.retryBlock().contains("attempts: 3"));
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

        assertTrue(builder.retryBlock().isEmpty());
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

        assertTrue(builder.retryBlock().contains("retry:"));
        assertTrue(builder.retryBlock().contains("attempts: 5"));
    }
}
