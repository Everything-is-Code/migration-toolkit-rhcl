package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderModContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                List.of(Map.of("header", "X-Req", "value", "v1", "op", "add")),
                null));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new HeaderModContributor().contribute(builder, ctx);

        assertFalse(builder.sharedFilters().isEmpty());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new HeaderModContributor().contribute(builder, ctx);

        assertTrue(builder.sharedFilters().isEmpty());
    }

    @Test
    void buildHeaderModificationFilters_requestDirection() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                List.of(Map.of("header", "X-Req", "value", "in", "op", "set")),
                null));
        String filters = HeaderModContributor.buildHeaderModificationFilters(service);
        assertTrue(filters.contains("RequestHeaderModifier"));
    }

    @Test
    void buildHeaderModificationFilters_deleteAndLiquid() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                null,
                List.of(
                        Map.of("header", "X-Del", "op", "delete"),
                        Map.of("header", "X-Liq", "value", "{{x}}", "value_type", "liquid"))));
        String filters = HeaderModContributor.buildHeaderModificationFilters(service);
        assertTrue(filters.contains("- X-Del"));
        assertTrue(filters.contains("liquid template"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                null,
                List.of(
                        Map.of("header", "X-Resp", "value", "out", "op", "set"),
                        Map.of("header", "X-Remove", "op", "delete"))));
        String filters = HeaderModContributor.buildHeaderModificationFilters(service);

        assertTrue(filters.contains("ResponseHeaderModifier"));
        assertTrue(filters.contains("name: X-Resp"));
        assertTrue(filters.contains("- X-Remove"));
    }
}
