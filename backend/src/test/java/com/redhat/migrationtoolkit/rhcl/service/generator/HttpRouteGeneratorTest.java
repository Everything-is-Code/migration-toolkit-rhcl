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
        assertTrue(yaml.contains("apiVersion: gateway.networking.k8s.io/v1"));
        assertTrue(yaml.contains("kind: HTTPRoute"));
        assertTrue(yaml.contains("name: route-api-route"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("parentRefs:"));
        assertTrue(yaml.contains("name: route-api-gateway"));
        assertTrue(yaml.contains("rules:"));
        assertTrue(yaml.contains("backendRefs:"));
        assertTrue(yaml.contains("type: PathPrefix"));
        assertTrue(yaml.contains("value: \"/api/v1\""));
        assertTrue(yaml.contains("method: GET"));
    }
}
