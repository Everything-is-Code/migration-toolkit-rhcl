package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("envoyfilter-maintenance.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("EnvoyFilter", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("maint-api-maintenance", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("maintenance_mode", annotations.get("3scale-migration/source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) configPatches.get(0).get("patch");
        @SuppressWarnings("unchecked")
        Map<String, Object> patchVal = (Map<String, Object>) value.get("value");
        @SuppressWarnings("unchecked")
        Map<String, Object> typedConfig = (Map<String, Object>) patchVal.get("typed_config");
        String inlineCode = (String) typedConfig.get("inlineCode");

        assertTrue(inlineCode.contains("respond"), "Lua script must call respond");
        assertTrue(inlineCode.contains("503"), "Lua script must embed the status code");
        assertTrue(inlineCode.contains("Under maintenance"), "Lua script must embed the message");
        assertTrue(inlineCode.contains("text/plain"), "Lua script must embed the content-type");
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
        assertTrue(yaml.contains("503"), "default status 503 must be present");
        assertTrue(yaml.contains("text/plain"), "default content-type must be present");
        assertTrue(yaml.contains("respond("), "Lua respond call must be present");
    }

    @Test
    void isConfigEnabled_nullPolicyOrConfigOrEnabled_isFalse() {
        assertFalse(MaintenanceModeEnvoyFilterGenerator.isConfigEnabled(null));

        Policy noConfig = new Policy();
        noConfig.name = "maintenance_mode";
        noConfig.enabled = true;
        noConfig.configuration = null;
        assertFalse(MaintenanceModeEnvoyFilterGenerator.isConfigEnabled(noConfig));

        Policy missingEnabled = GeneratorTestSupport.policyWithConfig("maintenance_mode", new HashMap<>());
        assertFalse(MaintenanceModeEnvoyFilterGenerator.isConfigEnabled(missingEnabled));
    }

    @Test
    void isConfigEnabled_stringTrue_isTrue() {
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", Map.of(
                "enabled", "true"));
        assertTrue(MaintenanceModeEnvoyFilterGenerator.isConfigEnabled(maintenance));
    }

    @Test
    void resolveConfigString_and_escapeLua_nullSafeDefaults() {
        assertEquals("503", MaintenanceModeEnvoyFilterGenerator.resolveConfigString(null, "503"));
        assertEquals("503", MaintenanceModeEnvoyFilterGenerator.resolveConfigString("null", "503"));
        assertEquals("503", MaintenanceModeEnvoyFilterGenerator.resolveConfigString("  ", "503"));
        assertEquals("418", MaintenanceModeEnvoyFilterGenerator.resolveConfigString("418", "503"));

        assertEquals("", MaintenanceModeEnvoyFilterGenerator.escapeLua(null));
        assertEquals("a\\\"b\\\\c\\nd", MaintenanceModeEnvoyFilterGenerator.escapeLua("a\"b\\c\nd"));
    }

    // ── Edge case 4.3 ────────────────────────────────────────────────────────

    @Test
    void generate_luaScript_preservesSpecialCharsInInlineCode() {
        String specialMessage = "--- : # Service unavailable ---";
        Policy maintenance = GeneratorTestSupport.policyWithConfig("maintenance_mode", Map.of(
                "enabled", true,
                "status", "503",
                "message", specialMessage,
                "message_content_type", "text/plain"));
        ApiService service = GeneratorTestSupport.basicService("Special API", "special-api");
        service.policies = List.of(maintenance);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = (Map<String, Object>) configPatches.get(0).get("patch");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) patch.get("value");
        @SuppressWarnings("unchecked")
        Map<String, Object> typedConfig = (Map<String, Object>) value.get("typed_config");
        String inlineCode = (String) typedConfig.get("inlineCode");

        assertNotNull(inlineCode, "inlineCode must be present in YAML");
        assertTrue(inlineCode.contains("---") || inlineCode.contains("Service unavailable"),
                "special chars --- and # in message must survive YAML round-trip via inlineCode");
    }
}
