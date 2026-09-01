package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteGeneratorManualTest {

    @Test
    void manualBinding_generatesHttpRouteWithoutCdi() {
        HttpRouteGenerator manual = new HttpRouteGenerator();
        manual.bindManual(new ManifestSerializer());
        manual.bindManualContributors(ManualHttpRouteContributorFactory.create());
        ApiService service = GeneratorTestSupport.serviceWithMappingRules("manual-route");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = manual.generate(ctx);

        assertNotNull(yaml);
        assertEquals("httproute.yaml", manual.outputKey());
        assertTrue(yaml.contains("manual-route-route"), yaml);
        assertTrue(yaml.contains("backendRefs:"), yaml);
    }

    @Test
    void manualBinding_nativeCors_injectsCorsFilter() {
        HttpRouteGenerator manual = new HttpRouteGenerator();
        manual.bindManual(new ManifestSerializer());
        manual.bindManualContributors(ManualHttpRouteContributorFactory.create());

        ConversionOptions options = new ConversionOptions();
        options.corsNative = true;
        ApiService service = GeneratorTestSupport.serviceWithMappingRules("cors-native");
        service.policies = List.of(GeneratorTestSupport.policyWithConfig("cors", Map.of(
                "allow_origin", List.of("https://app.example.com"),
                "allow_methods", List.of("GET"),
                "max_age", "120")));
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = manual.generate(ctx);

        assertTrue(yaml.contains("type: CORS"), yaml);
        assertTrue(yaml.contains("maxAge: 120"), yaml);
    }
}
