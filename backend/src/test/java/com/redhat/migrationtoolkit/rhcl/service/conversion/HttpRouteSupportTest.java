package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRouteSupportTest {

    @Test
    void toGatewayApiPathPrefix_braceAndTrailingSlash() {
        assertEquals("/", HttpRouteSupport.toGatewayApiPathPrefix(null));
        assertEquals("/users", HttpRouteSupport.toGatewayApiPathPrefix("/users/{id}/"));
        assertEquals("/v1", HttpRouteSupport.toGatewayApiPathPrefix("/v1/"));
    }

    @Test
    void selectBackendsForPath_longestMountPrefixWins() {
        List<ResolvedBackend> backends = List.of(
                ConversionSupportTestFixtures.resolvedBackend(
                        BackendType.INTERNAL, "root-backend", "/"),
                ConversionSupportTestFixtures.resolvedBackend(
                        BackendType.INTERNAL, "api-backend", "/api"));

        List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, "/api/v1");

        assertEquals(1, selected.size());
        assertEquals("api-backend", selected.get(0).refName);
    }

    @Test
    void selectBackendsForPath_noMatch_fallsBackToAll() {
        List<ResolvedBackend> backends = List.of(
                ConversionSupportTestFixtures.resolvedBackend(
                        BackendType.INTERNAL, "api-backend", "/api"));

        List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, "/other");

        assertEquals(1, selected.size());
        assertEquals("api-backend", selected.get(0).refName);
    }

    @Test
    void isMountPrefixOf_rootAndNestedPaths() {
        assertTrue(HttpRouteSupport.isMountPrefixOf("/", "/anything"));
        assertTrue(HttpRouteSupport.isMountPrefixOf("/api", "/api/v1"));
        assertFalse(HttpRouteSupport.isMountPrefixOf("/api", "/apiv1"));
    }

    @Test
    void formatBackendRefs_weightedWhenMultipleBackends() {
        List<ResolvedBackend> selected = List.of(
                new ResolvedBackend(BackendType.INTERNAL, "a-backend", "a-se", "a-dr",
                        null, 8080, false, "/a", 70, null),
                new ResolvedBackend(BackendType.INTERNAL, "b-backend", "b-se", "b-dr",
                        null, 8080, false, "/b", 30, null));

        String yaml = HttpRouteSupport.formatBackendRefs(selected);

        assertTrue(yaml.contains("name: a-backend"));
        assertTrue(yaml.contains("weight: 70"));
        assertTrue(yaml.contains("weight: 30"));
    }

    @Test
    void uniqueExternalHost_singleHost_returnsHost() {
        List<ResolvedBackend> selected = List.of(
                new ResolvedBackend(BackendType.EXTERNAL, "a-backend", "a-se", "a-dr",
                        "api.example.com", 443, true, "/", 1, null));

        assertEquals("api.example.com", HttpRouteSupport.uniqueExternalHost(selected));
    }

    @Test
    void uniqueExternalHost_multipleHosts_returnsNull() {
        List<ResolvedBackend> selected = List.of(
                new ResolvedBackend(BackendType.EXTERNAL, "a-backend", "a-se", "a-dr",
                        "a.example.com", 443, true, "/", 1, null),
                new ResolvedBackend(BackendType.EXTERNAL, "b-backend", "b-se", "b-dr",
                        "b.example.com", 443, true, "/b", 1, null));

        assertNull(HttpRouteSupport.uniqueExternalHost(selected));
    }

    @Test
    void yamlDoubleQuoted_escapesSpecialCharacters() {
        assertEquals("\"\"", HttpRouteSupport.yamlDoubleQuoted(null));
        assertEquals("\"*\"", HttpRouteSupport.yamlDoubleQuoted("*"));
        assertEquals("\"say \\\"hi\\\"\"", HttpRouteSupport.yamlDoubleQuoted("say \"hi\""));
    }

    @Test
    void toStringList_listCommaSeparatedAndScalar() {
        assertEquals(List.of("a", "b"), HttpRouteSupport.toStringList(List.of("a", "b")));
        assertEquals(List.of("GET", "POST"), HttpRouteSupport.toStringList("GET, POST"));
        assertTrue(HttpRouteSupport.toStringList(null).isEmpty());
    }

    @Test
    void isHeaderModificationPolicy_recognizesAliases() {
        assertFalse(HttpRouteSupport.isHeaderModificationPolicy(null));
        assertTrue(HttpRouteSupport.isHeaderModificationPolicy("headers"));
        assertTrue(HttpRouteSupport.isHeaderModificationPolicy("HEADER_MODIFICATION"));
    }

    @Test
    void findHeaderModificationPolicy_returnsEnabledPolicy() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        Policy enabled = ConversionSupportTestFixtures.policy("headers", true, null);
        Policy disabled = ConversionSupportTestFixtures.policy("header_modification", false, null);
        service.policies = List.of(disabled, enabled);

        assertEquals(enabled, HttpRouteSupport.findHeaderModificationPolicy(service));
    }

    @Test
    void collectMappingRulePaths_deduplicatesAndAddsRoot() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        MappingRule users = new MappingRule();
        users.httpMethod = "GET";
        users.pattern = "/users/{id}";
        MappingRule root = new MappingRule();
        root.httpMethod = "GET";
        root.pattern = "/";
        service.mappingRules = List.of(users, users, root);

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        HttpRouteSupport.collectMappingRulePaths(service, paths);

        assertTrue(paths.contains("/users"));
        assertTrue(paths.contains("/"));
    }

    @Test
    void collectMappingRulePaths_emptyRules_addsRoot() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        HttpRouteSupport.collectMappingRulePaths(service, paths);

        assertEquals(List.of("/"), new ArrayList<>(paths));
    }
}
