package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsFiltersContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.corsPolicy(Map.of(
                "allow_origin", List.of("https://app.example.com"),
                "allow_methods", List.of("GET", "POST"))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new CorsFiltersContributor().contribute(builder, ctx);

        assertTrue(builder.corsEnabled());
        assertFalse(builder.sharedFilters().isEmpty());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new CorsFiltersContributor().contribute(builder, ctx);

        assertFalse(builder.corsEnabled());
    }

    @Test
    void contribute_addsExpectedFragments_nativeCors() {
        Policy cors = ContributorTestFixtures.corsPolicy(Map.of(
                "allow_origin", List.of("https://a.example.com"),
                "allow_methods", List.of("get"),
                "allow_headers", List.of("X-Custom"),
                "allow_credentials", true,
                "max_age", 3600));
        String filters = CorsFiltersContributor.buildCorsFilters(cors, true);

        assertTrue(filters.contains("type: CORS"));
        assertTrue(filters.contains("allowCredentials: true"));
        assertTrue(filters.contains("maxAge: 3600"));
        assertTrue(filters.contains("GET"));
    }

    @Test
    void contribute_viaContributor_responseHeaderModifier() {
        Policy cors = ContributorTestFixtures.corsPolicy(Map.of(
                "allow_origin", List.of("https://b.example.com"),
                "allow_methods", List.of("POST")));
        ConversionOptions options = new ConversionOptions();
        options.corsNative = false;
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(cors);
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);

        new CorsFiltersContributor().contribute(builder, ctx);

        // sharedFilters is List<HTTPRouteFilter>; check it is non-empty and has correct type
        assertFalse(builder.sharedFilters().isEmpty());
        assertEquals("ResponseHeaderModifier", builder.sharedFilters().get(0).getType());
    }

    @Test
    void contribute_nativeCors_emptyOrigins_useWildcard() {
        Policy cors = ContributorTestFixtures.corsPolicy(Map.of());
        String filters = CorsFiltersContributor.buildCorsFilters(cors, true);
        assertTrue(filters.contains("\"*\""));
    }

    @Test
    void contribute_nativeCors_invalidMaxAge_ignored() {
        Policy cors = ContributorTestFixtures.corsPolicy(Map.of("max_age", "not-a-number"));
        String filters = CorsFiltersContributor.buildCorsFilters(cors, true);
        assertFalse(filters.contains("maxAge:"));
    }

    @Test
    void contribute_responseModifier_wildcardOrigin() {
        Policy cors = ContributorTestFixtures.corsPolicy(Map.of(
                "allow_origin", List.of("*"),
                "allow_methods", List.of("GET"),
                "allow_credentials", true,
                "max_age", 120));
        String filters = CorsFiltersContributor.buildCorsFilters(cors, false);
        assertTrue(filters.contains("Access-Control-Allow-Credentials"));
        assertTrue(filters.contains("Access-Control-Max-Age"));
    }

    @Test
    void contribute_nullConfiguration_returnsEmpty() {
        Policy cors = new com.redhat.migrationtoolkit.rhcl.model.Policy();
        cors.name = "cors";
        cors.enabled = true;
        cors.configuration = null;
        assertEquals("", CorsFiltersContributor.buildCorsFilters(cors, true));
    }
}
