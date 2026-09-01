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

import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPBackendRefBuilder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void build_omitsMigratedFromLabelWhenDisabled() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionOptions options = new ConversionOptions();
        options.includeMigratedFromLabel = false;
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, options, new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);

        String yaml = builder.build();

        assertTrue(yaml.contains("app:") && yaml.contains("demo-api"), yaml);
        assertFalse(yaml.contains("migrated-from"), yaml);
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
    void accessors_exposeBuilderState() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);

        assertEquals("demo-api", builder.name());
        assertEquals("ns", builder.namespace());
        builder.setRawCorsFilterYaml("- type: CORS\n");
        assertTrue(builder.rawCorsFilterYaml().contains("type: CORS"));
        builder.setRawCorsFilterYaml("  ");
        assertEquals(null, builder.rawCorsFilterYaml());
    }

    @Test
    void injectYamlComments_emptyOrMissingSpec_returnsOriginal() {
        assertEquals("plain", HttpRouteBuilder.injectYamlComments("plain", List.of("note")));
        assertEquals("y", HttpRouteBuilder.injectYamlComments("y", List.of()));
    }

    @Test
    void injectCorsFilters_blankFragment_returnsOriginal() {
        assertEquals("yaml", HttpRouteBuilder.injectCorsFilters("yaml", "  "));
    }

    @Test
    void build_malformedDiscoveryMarker_isIgnored() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        builder.setDiscoveryMarker("no-colon-marker");
        builder.setDiscoveryMarker(":empty-value");
        builder.setDiscoveryMarker("empty-key:");

        String yaml = builder.build();

        assertFalse(yaml.contains("no-colon-marker"));
        assertFalse(yaml.contains("empty-value"));
    }

    @Test
    void build_withYamlComments_injectsBeforeSpec() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        builder.addYamlComment("operator note");
        builder.addRule(new HTTPRouteRuleBuilder()
                .withMatches(new HTTPRouteMatchBuilder()
                        .withNewPath().withType("PathPrefix").withValue("/").endPath()
                        .build())
                .withBackendRefs(new HTTPBackendRefBuilder().withName("demo-backend").withPort(8080).build())
                .build());

        String yaml = builder.build();

        assertTrue(yaml.indexOf("# operator note") < yaml.indexOf("spec:"), yaml);
    }

    @Test
    void build_withRawCors_injectsIntoRuleOutput() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        builder.setRawCorsFilterYaml("- type: CORS\n  cors:\n    allowOrigins:\n    - \"*\"");
        builder.addRule(new HTTPRouteRuleBuilder()
                .withMatches(new HTTPRouteMatchBuilder()
                        .withNewPath().withType("PathPrefix").withValue("/api").endPath()
                        .build())
                .withBackendRefs(new HTTPBackendRefBuilder().withName("demo-backend").withPort(8080).build())
                .build());

        String yaml = builder.build();

        assertTrue(yaml.contains("type: CORS"), yaml);
        assertTrue(yaml.indexOf("filters:") < yaml.indexOf("matches:"), yaml);
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
