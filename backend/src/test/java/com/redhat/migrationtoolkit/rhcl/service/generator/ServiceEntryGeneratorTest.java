package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceEntryGeneratorTest {

    private final ServiceEntryGenerator generator = new ServiceEntryGenerator();

    @Test
    void applies_returnsTrue_whenExternalBackendPresent() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "ext-api", "https://api.external.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenOnlyInternalBackend() {
        ApiService service = GeneratorTestSupport.basicService("Internal API", "internal-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesServiceEntryAndExternalNameService() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "ext-api", "https://api.external.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(yaml);

        assertNotNull(yaml);
        assertEquals("serviceentry.yaml", generator.outputKey());
        assertEquals(2, docs.size(), "must produce ServiceEntry + Service documents");

        Map<String, Object> seDoc = docs.get(0);
        assertEquals("networking.istio.io/v1alpha3", seDoc.get("apiVersion"));
        assertEquals("ServiceEntry", seDoc.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> seMeta = (Map<String, Object>) seDoc.get("metadata");
        assertEquals("ext-api-external", seMeta.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, seMeta.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> seSpec = (Map<String, Object>) seDoc.get("spec");
        @SuppressWarnings("unchecked")
        List<String> hosts = (List<String>) seSpec.get("hosts");
        assertTrue(hosts.contains("api.external.example.com"));

        Map<String, Object> svcDoc = docs.get(1);
        assertEquals("v1", svcDoc.get("apiVersion"));
        assertEquals("Service", svcDoc.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> svcSpec = (Map<String, Object>) svcDoc.get("spec");
        assertEquals("ExternalName", svcSpec.get("type"));
    }

    // ── Edge case 4.1 ────────────────────────────────────────────────────────

    @Test
    void applies_returnsFalse_whenOnlyInternalBackendExplicit() {
        ApiService service = GeneratorTestSupport.basicService("Int API", "int-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertFalse(generator.applies(ctx),
                "applies() must return false when no external backend is present");
    }

    @Test
    void generate_httpExternalBackend_producesValidServiceEntryWithHttpProtocol() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "plain-api", "http://api.plain.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx), "http external backend must trigger applies()");

        String yaml = generator.generate(ctx);
        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(yaml);

        assertEquals(2, docs.size());
        Map<String, Object> seDoc = docs.get(0);
        assertEquals("ServiceEntry", seDoc.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> seSpec = (Map<String, Object>) seDoc.get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) seSpec.get("ports");
        assertEquals(1, ports.size());
        assertEquals("http", ports.get(0).get("name"));
        assertEquals("HTTP", ports.get(0).get("protocol"));
    }

    @Test
    void generate_httpsExternalBackend_usesHttpsProtocol() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "secure-api", "https://secure.external.example.com");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> seSpec = (Map<String, Object>) docs.get(0).get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) seSpec.get("ports");
        assertEquals("HTTPS", ports.get(0).get("protocol"));
        assertEquals("https", ports.get(0).get("name"));
    }

    @Test
    void generate_omitsMigratedFromLabel_whenDisabled() {
        ApiService service = GeneratorTestSupport.serviceWithExternalBackend(
                "ext-api", "https://api.external.example.com");
        ConversionOptions options = new ConversionOptions();
        options.includeMigratedFromLabel = false;
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        String yaml = generator.generate(ctx);
        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) ((Map<String, Object>) docs.get(0).get("metadata")).get("labels");
        assertFalse(labels.containsKey("migrated-from"));
    }
}
