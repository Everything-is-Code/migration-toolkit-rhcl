package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiProductGeneratorTest {

    private final ApiProductGenerator generator = new ApiProductGenerator();

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("Product API", "product-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesApiProductWithTargetRef() {
        ApiService service = GeneratorTestSupport.basicService("Product API", "product-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("apiproduct.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: devportal.kuadrant.io/v1alpha1"));
        assertTrue(yaml.contains("kind: APIProduct"));
        assertTrue(yaml.contains("name: product-api"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("displayName: \"Product API\""));
        assertTrue(yaml.contains("targetRef:"));
        assertTrue(yaml.contains("kind: HTTPRoute"));
        assertTrue(yaml.contains("name: product-api-route"));
    }
}
