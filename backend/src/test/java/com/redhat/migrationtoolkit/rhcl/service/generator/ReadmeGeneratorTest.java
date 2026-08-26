package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ReadmeGeneratorTest {

    @Inject
    ReadmeGenerator generator;

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("Readme API", "readme-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesMarkdownWithExpectedSections() {
        ApiService service = GeneratorTestSupport.basicService("Readme API", "readme-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String readme = generator.generate(ctx);

        assertNotNull(readme);
        assertEquals("README.md", generator.outputKey());
        assertTrue(readme.contains("Connectivity Link Migration"));
        assertTrue(readme.contains("## Overview"));
        assertTrue(readme.contains("## Files"));
        assertTrue(readme.contains("## Notes"));
        assertTrue(readme.contains("readme-api"));
        assertTrue(readme.contains(GeneratorTestSupport.NAMESPACE));
    }
}
