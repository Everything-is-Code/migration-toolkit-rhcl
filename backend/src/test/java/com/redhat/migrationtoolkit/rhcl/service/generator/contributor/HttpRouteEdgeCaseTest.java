package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.UpstreamSupport;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilterBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Edge-case tests for typed HTTPRoute migration (#262 task 5). */
class HttpRouteEdgeCaseTest {

    @Test
    void addAnnotation_duplicateKey_lastWriteWins() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        builder.addAnnotation("demo", "first");
        builder.addAnnotation("demo", "second");

        String yaml = builder.build();
        assertTrue(yaml.contains("second"));
        assertFalse(yaml.contains("first"));
    }

    @Test
    void corsFiltersContributor_emptyOrigins_producesValidFilter() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("cors", true,
                Map.of("allow_origin", List.of())));

        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        new CorsFiltersContributor().contribute(builder, ctx);

        assertFalse(builder.sharedFilters().isEmpty());
        HTTPRouteFilter filter = builder.sharedFilters().get(0);
        assertNotNull(filter.getResponseHeaderModifier());
        assertNotNull(filter.getResponseHeaderModifier().getSet());
        assertFalse(filter.getResponseHeaderModifier().getSet().isEmpty());
    }

    @Test
    void timeoutsContributor_zeroReadTimeout_serializesAsZeroSeconds() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("upstream_connection", true,
                Map.of("read_timeout", 0)));

        HTTPRouteTimeouts timeouts = TimeoutsContributor.buildTimeouts(service);
        assertNotNull(timeouts);
        assertEquals("0s", timeouts.getRequest());
    }

    @Test
    void headerModContributor_liquidHeader_emitsYamlComment() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("headers", true, Map.of(
                "response", List.of(Map.of(
                        "header", "X-Liq", "value", "{{x}}", "value_type", "liquid")))));

        ConversionContext ctx = ContributorTestFixtures.context(service);
        HttpRouteBuilder builder = ContributorTestFixtures.httpRouteBuilder(ctx);
        new HeaderModContributor().contribute(builder, ctx);

        String yaml = builder.build();
        assertTrue(yaml.contains("liquid template"));
    }

    @Test
    void httpRouteSupport_buildRuleFilters_preservesNonRewriteFilters() {
        List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(
                List.of(), List.of(new HTTPRouteFilterBuilder()
                        .withType("ResponseHeaderModifier")
                        .withNewResponseHeaderModifier()
                        .withAdd(List.of())
                        .endResponseHeaderModifier()
                        .build()));

        assertEquals(1, filters.size());
        assertEquals("ResponseHeaderModifier", filters.get(0).getType());
    }

    @Test
    void upstreamSupport_globalUrl_blankCatchAll_returnsBlank() {
        Policy upstream = ContributorTestFixtures.policy("upstream", true,
                Map.of("rules", List.of(Map.of("url", "  "))));
        String url = UpstreamSupport.globalUrl(upstream);
        assertTrue(url == null || url.isBlank());
    }

    @Test
    void injectYamlComments_insertsBeforeSpec() {
        String yaml = "kind: HTTPRoute\nmetadata:\n  name: demo\nspec:\n  rules: []\n";
        String merged = HttpRouteBuilder.injectYamlComments(yaml, List.of("manual step required"));
        assertTrue(merged.indexOf("# manual step required") < merged.indexOf("spec:"));
    }
}
