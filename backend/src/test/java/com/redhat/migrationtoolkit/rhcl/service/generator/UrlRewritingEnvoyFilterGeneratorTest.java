package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
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

        assertNotNull(yaml);
        assertEquals("envoyfilter-url-rewriting.yaml", generator.outputKey());
        assertTrue(yaml.contains("kind: EnvoyFilter"));
        assertTrue(yaml.contains("name: rewrite-api-url-rewriting"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("3scale-migration/source: url_rewriting"));
        assertTrue(yaml.contains("envoy.filters.http.lua"));
        assertTrue(yaml.contains("inlineCode:"));
        assertTrue(yaml.contains("envoy_on_request"));
    }
}
