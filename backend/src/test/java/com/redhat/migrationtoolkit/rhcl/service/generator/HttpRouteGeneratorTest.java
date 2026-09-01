package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HttpRouteGeneratorTest {

    @Inject
    HttpRouteGenerator generator;

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.serviceWithMappingRules("route-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesHttpRouteWithParentRefsAndRules() {
        ApiService service = GeneratorTestSupport.serviceWithMappingRules("route-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("httproute.yaml", generator.outputKey());
        assertTrue(yaml.contains("gateway.networking.k8s.io/v1"), yaml);
        assertTrue(yaml.contains("HTTPRoute"), yaml);
        assertTrue(yaml.contains("route-api-route"), yaml);
        assertTrue(yaml.contains(GeneratorTestSupport.NAMESPACE), yaml);
        assertTrue(yaml.contains("parentRefs:"), yaml);
        assertTrue(yaml.contains("route-api-gateway"), yaml);
        assertTrue(yaml.contains("rules:"), yaml);
        assertTrue(yaml.contains("backendRefs:"), yaml);
        assertTrue(yaml.contains("PathPrefix"), yaml);
        assertTrue(yaml.contains("/api/v1"), yaml);
        assertTrue(yaml.contains("GET"), yaml);
    }
}
