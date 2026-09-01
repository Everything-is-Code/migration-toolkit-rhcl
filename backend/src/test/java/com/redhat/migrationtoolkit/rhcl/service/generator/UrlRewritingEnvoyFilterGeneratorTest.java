package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UrlRewritingEnvoyFilterGeneratorTest {

    @Inject
    UrlRewritingEnvoyFilterGenerator generator;

    @Test
    void applies_returnsTrue_whenUrlRewritingPolicyHasCommands() {
        Policy urlRewriting = GeneratorTestSupport.policyWithConfig("url_rewriting", Map.of(
                "commands", List.of(Map.of("op", "sub", "regex", "/old", "replace", "/new"))));
        ApiService service = GeneratorTestSupport.basicService("Rewrite API", "rewrite-api");
        service.policies = List.of(urlRewriting);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenUrlRewritingPolicyAbsent() {
        ApiService service = GeneratorTestSupport.basicService("Plain API", "plain-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesEnvoyFilterWithLuaFilter() {
        Policy urlRewriting = GeneratorTestSupport.policyWithConfig("url_rewriting", Map.of(
                "commands", List.of(Map.of("op", "sub", "regex", "/old", "replace", "/new"))));
        ApiService service = GeneratorTestSupport.basicService("Rewrite API", "rewrite-api");
        service.policies = List.of(urlRewriting);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("envoyfilter-url-rewriting.yaml", generator.outputKey());
        assertEquals("networking.istio.io/v1alpha3", parsed.get("apiVersion"));
        assertEquals("EnvoyFilter", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("rewrite-api-url-rewriting", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
        assertEquals("url_rewriting", annotations.get("3scale-migration/source"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configPatches = (List<Map<String, Object>>) spec.get("configPatches");
        assertNotNull(configPatches);
        @SuppressWarnings("unchecked")
        Map<String, Object> patchValue = (Map<String, Object>) configPatches.get(0).get("patch");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) patchValue.get("value");
        assertEquals("envoy.filters.http.lua", value.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> typedConfig = (Map<String, Object>) value.get("typed_config");
        String inlineCode = (String) typedConfig.get("inlineCode");
        assertTrue(inlineCode.contains("envoy_on_request"), "Lua script must contain entry function");
    }

    // ── Edge case 4.6 ────────────────────────────────────────────────────────

    @Test
    void applies_returnsFalse_whenCommandsListIsEmpty() {
        Policy urlRewriting = GeneratorTestSupport.policyWithConfig("url_rewriting", Map.of(
                "commands", List.of()));
        ApiService service = GeneratorTestSupport.basicService("Rewrite API", "rewrite-api");
        service.policies = List.of(urlRewriting);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when commands list is empty");
    }

    @Test
    void generate_commandWithEmptyRegex_doesNotCrashAndProducesEmptyRules() {
        Policy urlRewriting = GeneratorTestSupport.policyWithConfig("url_rewriting", Map.of(
                "commands", List.of(Map.of("op", "sub", "regex", "", "replace", "/new"))));
        ApiService service = GeneratorTestSupport.basicService("Rewrite API", "rewrite-api");
        service.policies = List.of(urlRewriting);

        // applies() is false for empty regex, but buildLuaScript must not crash
        List<Map<String, Object>> commands = List.of(Map.of("op", "sub", "regex", "", "replace", "/new"));
        String script = UrlRewritingEnvoyFilterGenerator.buildLuaScript(commands);

        assertNotNull(script, "buildLuaScript must not return null for empty regex");
        assertTrue(script.contains("envoy_on_request"), "script must still contain the entry function");
        assertFalse(script.contains("string.gsub(path, \"\","),
                "empty-regex command must be skipped in the script body");
    }

    @Test
    void buildLuaScript_gsubOp_usesGlobalReplace() {
        List<Map<String, Object>> commands = List.of(Map.of(
                "op", "gsub",
                "regex", "/old",
                "replace", "/new"));
        String script = UrlRewritingEnvoyFilterGenerator.buildLuaScript(commands);

        assertTrue(script.contains("string.gsub(path,"));
        assertFalse(script.contains(", 1)"), "gsub must not use single-replace limit");
    }
}
