package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceGeneratorRegistryTest {

    @Test
    void manualRegistry_resolvesGatewayByOutputKey() {
        ConversionService conversionService = new ConversionService();
        ResourceGeneratorRegistry registry = ResourceGeneratorRegistry.manual();

        ApiService service = new ApiService();
        service.name = "my-api";
        service.systemName = "my-api";

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        var files = registry.generateAll(ctx);

        assertTrue(files.containsKey("gateway.yaml"));
        assertEquals("Gateway", YamlAssertions.parse(files.get("gateway.yaml")).get("kind"));
    }
}
