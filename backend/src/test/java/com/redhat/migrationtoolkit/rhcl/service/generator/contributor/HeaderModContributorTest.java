package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
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
        List<HTTPRouteFilter> filters = HeaderModContributor.buildHeaderModificationFilters(service);
        assertFalse(filters.isEmpty());
        assertTrue(filters.stream().anyMatch(f -> "RequestHeaderModifier".equals(f.getType())));
    }

    @Test
    void buildHeaderModificationFilters_deleteAndLiquid() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                null,
                List.of(
                        Map.of("header", "X-Del", "op", "delete"),
                        Map.of("header", "X-Liq", "value", "{{x}}", "value_type", "liquid"))));
        List<HTTPRouteFilter> filters = HeaderModContributor.buildHeaderModificationFilters(service);
        // delete op emits a filter; liquid is skipped (no Gateway API representation)
        assertFalse(filters.isEmpty());
        assertTrue(filters.stream().anyMatch(f -> "ResponseHeaderModifier".equals(f.getType())));
        // liquid header should not appear as a filter name
        assertTrue(filters.stream()
                .filter(f -> f.getResponseHeaderModifier() != null)
                .flatMap(f -> f.getResponseHeaderModifier().getRemove().stream())
                .anyMatch(name -> "X-Del".equals(name)));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.headerModificationPolicy(
                null,
                List.of(
                        Map.of("header", "X-Resp", "value", "out", "op", "set"),
                        Map.of("header", "X-Remove", "op", "delete"))));
        List<HTTPRouteFilter> filters = HeaderModContributor.buildHeaderModificationFilters(service);

        assertFalse(filters.isEmpty());
        assertTrue(filters.stream().anyMatch(f -> "ResponseHeaderModifier".equals(f.getType())));
        // X-Resp should be in set
        assertTrue(filters.stream()
                .filter(f -> f.getResponseHeaderModifier() != null)
                .flatMap(f -> f.getResponseHeaderModifier().getSet().stream())
                .anyMatch(h -> "X-Resp".equals(h.getName())));
        // X-Remove should be in remove
        assertTrue(filters.stream()
                .filter(f -> f.getResponseHeaderModifier() != null)
                .flatMap(f -> f.getResponseHeaderModifier().getRemove().stream())
                .anyMatch(name -> "X-Remove".equals(name)));
    }
}
