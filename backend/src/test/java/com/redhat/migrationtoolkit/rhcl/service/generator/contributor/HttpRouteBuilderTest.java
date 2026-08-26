package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
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
        builder.appendAnnotationBody("    demo-annotation: \"yes\"\n");
        builder.appendRule("""
    - matches:
        - path:
            type: PathPrefix
            value: "/"
      backendRefs:
        - name: demo-api-backend
          port: 8080
""");

        String yaml = builder.build();

        assertTrue(yaml.contains("name: demo-api-route"));
        assertTrue(yaml.contains("namespace: ns"));
        assertTrue(yaml.contains("demo-annotation: \"yes\""));
        assertTrue(yaml.contains("name: demo-api-gateway"));
        assertTrue(yaml.contains("name: demo-api-backend"));
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
}
