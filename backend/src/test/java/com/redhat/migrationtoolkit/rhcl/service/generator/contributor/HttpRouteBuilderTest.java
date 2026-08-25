package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import org.junit.jupiter.api.Test;

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
}
