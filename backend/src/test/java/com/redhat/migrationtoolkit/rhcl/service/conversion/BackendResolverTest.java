package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendResolverTest {

    private BackendResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BackendResolver();
    }

    @Test
    void resolveBackends_emptyBackends_returnsDefaultInternal() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");

        List<ResolvedBackend> resolved = resolver.resolveBackends(service, "demo-api", null, false);

        assertEquals(1, resolved.size());
        assertEquals(BackendType.INTERNAL, resolved.get(0).type);
        assertEquals("demo-api-backend", resolved.get(0).refName);
        assertEquals("/", resolved.get(0).mountPath);
    }

    @Test
    void resolveBackends_singleInternalBackend_usesServiceRef() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo-ns.svc:8080", "/api"));

        List<ResolvedBackend> resolved = resolver.resolveBackends(service, "demo-api", null, false);

        assertEquals(1, resolved.size());
        ResolvedBackend backend = resolved.get(0);
        assertEquals(BackendType.INTERNAL, backend.type);
        assertEquals("my-svc", backend.refName);
        assertEquals("/api", backend.mountPath);
    }

    @Test
    void resolveBackends_singleExternalBackend_extractsHost() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "ext", "https://api.example.com/v1", "/"));

        List<ResolvedBackend> resolved = resolver.resolveBackends(service, "demo-api", null, false);

        assertEquals(1, resolved.size());
        ResolvedBackend backend = resolved.get(0);
        assertEquals(BackendType.EXTERNAL, backend.type);
        assertEquals("api.example.com", backend.externalHost);
        assertEquals("demo-api-backend", backend.refName);
    }

    @Test
    void resolveBackends_multipleBackends_preservesMountPathsAndWeights() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "a", "https://a.example.com", "/a", 70));
        service.backends.add(ConversionSupportTestFixtures.backend(
                "b", "https://b.example.com", "/b", 30));

        List<ResolvedBackend> resolved = resolver.resolveBackends(service, "demo-api", null, false);

        assertEquals(2, resolved.size());
        assertEquals("/a", resolved.get(0).mountPath);
        assertEquals(70, resolved.get(0).weight);
        assertEquals("demo-api-a-backend", resolved.get(0).refName);
        assertEquals("/b", resolved.get(1).mountPath);
        assertEquals(30, resolved.get(1).weight);
    }

    @Test
    void resolveBackends_overrideUrl_replacesAllBackends() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "ignored", "https://ignored.example.com", "/ignored"));

        List<ResolvedBackend> resolved = resolver.resolveBackends(
                service, "demo-api", "https://override.example.com", false);

        assertEquals(1, resolved.size());
        assertEquals(BackendType.EXTERNAL, resolved.get(0).type);
        assertEquals("override.example.com", resolved.get(0).externalHost);
        assertEquals("/", resolved.get(0).mountPath);
    }

    @Test
    void normalizeMountPath_nullOrBlank_returnsRoot() {
        assertEquals("/", BackendResolver.normalizeMountPath(null));
        assertEquals("/", BackendResolver.normalizeMountPath("  "));
    }

    @Test
    void normalizeMountPath_nonBlank_trimsValue() {
        assertEquals("/v1", BackendResolver.normalizeMountPath("  /v1  "));
    }

    @Test
    void detectBackendType_internalClusterService() {
        assertEquals(BackendType.INTERNAL, resolver.detectBackendType(null));
        assertEquals(BackendType.INTERNAL, resolver.detectBackendType(""));
        assertEquals(BackendType.INTERNAL,
                resolver.detectBackendType("http://my-svc.demo.svc:8080"));
        assertEquals(BackendType.INTERNAL,
                resolver.detectBackendType("http://my-svc.demo.svc.cluster.local:8080"));
        assertEquals(BackendType.INTERNAL, resolver.detectBackendType("http://localhost:8080"));
    }

    @Test
    void detectBackendType_externalHostWithDot() {
        assertEquals(BackendType.EXTERNAL,
                resolver.detectBackendType("https://api.example.com"));
    }

    @Test
    void disambiguateBackendNames_collision_appendsNumericSuffix() {
        ResolvedBackend first = new ResolvedBackend(
                BackendType.EXTERNAL, "demo-api-backend", "demo-api-external",
                "demo-api-backend-tls", "a.example.com", 443, true, "/", 1, null);
        ResolvedBackend second = new ResolvedBackend(
                BackendType.EXTERNAL, "demo-api-backend", "demo-api-external",
                "demo-api-backend-tls", "b.example.com", 443, true, "/b", 1, null);

        List<ResolvedBackend> disambiguated = resolver.disambiguateBackendNames(
                new ArrayList<>(List.of(first, second)));

        assertEquals("demo-api-backend", disambiguated.get(0).refName);
        assertEquals("demo-api-backend-2", disambiguated.get(1).refName);
        assertEquals("demo-api-external-2", disambiguated.get(1).seName);
        assertEquals("demo-api-backend-tls-2", disambiguated.get(1).drName);
    }

    @Test
    void disambiguateBackendNames_singleEntry_returnsSameInstance() {
        ResolvedBackend only = ConversionSupportTestFixtures.resolvedBackend(
                BackendType.INTERNAL, "demo-api-backend", "/");

        List<ResolvedBackend> disambiguated = resolver.disambiguateBackendNames(List.of(only));

        assertEquals(1, disambiguated.size());
        assertSame(only, disambiguated.get(0));
    }
}
