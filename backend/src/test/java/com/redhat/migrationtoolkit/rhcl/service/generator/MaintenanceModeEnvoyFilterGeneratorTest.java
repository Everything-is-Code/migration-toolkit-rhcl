package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MaintenanceModeEnvoyFilterGeneratorTest {

    @Inject
    MaintenanceModeEnvoyFilterGenerator generator;

    @Test
    void applies_returnsFalse_whenMaintenancePolicyAbsent() {
        ApiService service = GeneratorTestSupport.basicService("Plain API", "plain-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenConfigEnabledFalse() {
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", Map.of(
                "enabled", false,
                "status", 503,
                "message", "Under maintenance"));
        ApiService service = GeneratorTestSupport.basicService("Maint API", "maint-api");
        service.policies = List.of(maintenance);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void applies_returnsTrue_whenConfigEnabledTrue() {
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", Map.of(
                "enabled", true,
                "status", 503,
                "message", "Under maintenance",
                "message_content_type", "text/plain"));
        ApiService service = GeneratorTestSupport.basicService("Maint API", "maint-api");
        service.policies = List.of(maintenance);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_encodesConfiguredStatusBodyAndContentType() {
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", Map.of(
                "enabled", true,
                "status", 503,
                "message", "Under maintenance",
                "message_content_type", "text/plain"));
        ApiService service = GeneratorTestSupport.basicService("Maint API", "maint-api");
        service.policies = List.of(maintenance);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("envoyfilter-maintenance.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: EnvoyFilter"));
        assertTrue(yaml.contains("name: maint-api-maintenance"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: maintenance_mode"));
        assertTrue(yaml.contains("envoy.filters.http.lua"));
        assertTrue(yaml.contains("envoy_on_request"));
        assertTrue(yaml.contains("respond"));
        assertTrue(yaml.contains("\":status\"] = \"503\"") || yaml.contains("\":status\"]=\"503\""));
        assertTrue(yaml.contains("Under maintenance"));
        assertTrue(yaml.contains("text/plain"));
    }

    @Test
    void generate_blankFields_useDefaults() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("enabled", true);
        cfg.put("status", "");
        cfg.put("message", "");
        cfg.put("message_content_type", "");
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", cfg);
        ApiService service = GeneratorTestSupport.basicService("Maint API", "maint-api");
        service.policies = List.of(maintenance);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertTrue(yaml.contains("\":status\"] = \"503\"") || yaml.contains("\":status\"]=\"503\""));
        assertTrue(yaml.contains("text/plain"));
        // Empty body still passed to respond as ""
        assertTrue(yaml.contains("respond("));
    }
}
