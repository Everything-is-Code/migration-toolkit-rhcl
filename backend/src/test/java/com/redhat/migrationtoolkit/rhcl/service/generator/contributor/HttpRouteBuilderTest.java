package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteBuilderTest {

    @Test
    void build_assemblesMetadataAndRules() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        builder.addAnnotation("demo-annotation", "yes");
        builder.addRule(new HTTPRouteRuleBuilder()
                .withMatches(new HTTPRouteMatchBuilder()
                        .withNewPath().withType("PathPrefix").withValue("/").endPath()
                        .build())
                .build());

        String yaml = builder.build();

        // Fabric8 serializes string values with double quotes; check value substrings
        assertTrue(yaml.contains("demo-api-route"), yaml);
        assertTrue(yaml.contains("demo-annotation"), yaml);
        assertTrue(yaml.contains("demo-api-gateway"), yaml);
    }

    @Test
    void effectiveBackends_returnsOverrideWhenSet() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);

        assertSame(builder.backends(), builder.effectiveBackends());

        ResolvedBackend override = new ResolvedBackend(
                BackendType.EXTERNAL, "override-backend", "se", "dr",
                "override.example.com", 443, true, "/", null, "https://override.example.com");
        builder.setOverrideBackends(List.of(override));

        assertEquals(1, builder.effectiveBackends().size());
        assertEquals("override-backend", builder.effectiveBackends().get(0).refName);
        assertSame(ctx.resolvedBackends, builder.backends());
    }

    @Test
    void build_withDiscoveryMarker_addsAnnotation() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        builder.setDiscoveryMarker("x-test-marker: test-value");

        String yaml = builder.build();

        assertTrue(yaml.contains("x-test-marker"), yaml);
        assertTrue(yaml.contains("test-value"), yaml);
    }

    @Test
    void injectCorsFilters_appendsCorsBeforeMatchesWhenFiltersExist() {
        // Use 4-space indented markers matching actual Fabric8 serializer output
        // (rule keys are at 4-space indent inside spec.rules list items)
        String base = "spec:\n  rules:\n  - backendRefs:\n    - name: demo-backend\n      port: 8080\n"
                + "    filters:\n    - type: URLRewrite\n      urlRewrite:\n        hostname: api.example.com\n"
                + "    matches:\n    - path:\n        type: PathPrefix\n        value: /\n";
        String cors = "- type: CORS\n  cors:\n    allowOrigins:\n    - \"*\"";

        String merged = HttpRouteBuilder.injectCorsFilters(base, cors);

        // CORS items are injected into the filters section before matches
        assertTrue(merged.contains("type: CORS"), merged);
        assertTrue(merged.contains("type: URLRewrite"), merged);
        // Both filter types appear before matches
        assertTrue(merged.indexOf("type: CORS") < merged.indexOf("matches:"), merged);
        assertTrue(merged.indexOf("type: URLRewrite") < merged.indexOf("matches:"), merged);
    }

    @Test
    void injectCorsFilters_createsFiltersSectionWhenAbsent() {
        // Use 4-space indented markers matching actual Fabric8 serializer output
        String base = "spec:\n  rules:\n  - backendRefs:\n    - name: demo-backend\n      port: 8080\n"
                + "    matches:\n    - path:\n        type: PathPrefix\n        value: /\n";
        String cors = "- type: CORS\n  cors:\n    allowCredentials: true";

        String merged = HttpRouteBuilder.injectCorsFilters(base, cors);

        assertTrue(merged.contains("    filters:\n"), merged);
        assertTrue(merged.contains("type: CORS"), merged);
        assertTrue(merged.indexOf("filters:") < merged.indexOf("matches:"), merged);
    }

    @Test
    void build_withNoRules_producesValidHttpRoute() {
        ApiService service = new ApiService();
        service.name = "zero-rules";
        service.systemName = "zero-rules";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);

        String yaml = builder.build();

        assertTrue(yaml.contains("HTTPRoute"), yaml);
        assertTrue(yaml.contains("zero-rules-route"), yaml);
    }
}
