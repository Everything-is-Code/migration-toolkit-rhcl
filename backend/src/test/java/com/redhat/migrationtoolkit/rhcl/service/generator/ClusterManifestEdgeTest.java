package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterManifestEdgeTest {

    @Test
    void grantListsEachServiceOnceAndSplitsNamespaces() {
        ApiService service = serviceWithIssuer("https://rhbk.apps.example.com/realms/nb");
        service.backends = List.of(
                backend("accounts", "http://accounts.neuralbank-stack.svc.cluster.local:8080"),
                backend("accounts-again", "http://accounts.neuralbank-stack.svc.cluster.local:8080"),
                backend("loans", "http://loans.other-stack.svc.cluster.local:8080"),
                backend("local", "http://local.business-apis.svc.cluster.local:8080"),
                backend("external", "https://pay.example.com"));

        ConversionContext ctx = GeneratorTestSupport.context(service, "business-apis", new ConversionOptions());
        String grant = new ReferenceGrantGenerator().generate(ctx);

        assertTrue(grant.contains("---\n"));
        assertTrue(grant.contains("namespace: neuralbank-stack"));
        assertTrue(grant.contains("namespace: other-stack"));
        assertTrue(grant.contains("name: accounts\n"));
        assertTrue(grant.contains("name: loans\n"));
        assertFalse(grant.contains("name: local\n"));
        assertFalse(grant.contains("name: pay"));
        assertEqualsOnce(grant, "name: accounts\n");
    }

    @Test
    void sameNamespaceBackendOmitsGrantAndDocsNamespace() {
        ApiService service = serviceWithIssuer("https://rhbk.apps.example.com/realms/nb");
        service.backends = List.of(backend("local", "http://local.business-apis.svc.cluster.local:8080"));

        ConversionContext ctx = GeneratorTestSupport.context(service, "business-apis", new ConversionOptions());
        ReferenceGrantGenerator grants = new ReferenceGrantGenerator();
        DocsRouteGenerator docs = new DocsRouteGenerator();

        assertFalse(grants.applies(ctx));
        assertTrue(docs.applies(ctx));
        String route = docs.generate(ctx);
        assertTrue(route.contains("hostnames:"));
        assertFalse(route.contains("\n          namespace:"));
    }

    @Test
    void missingInternalBackendAndDiscoverySkipClusterExtras() {
        ApiService external = serviceWithIssuer("https://idp.example.com/realms/nb");
        external.backends = List.of(backend("pay", "https://pay.example.com"));
        ConversionContext externalCtx = GeneratorTestSupport.context(external, "business-apis", new ConversionOptions());

        DocsRouteGenerator docs = new DocsRouteGenerator();
        OpenShiftRouteGenerator route = new OpenShiftRouteGenerator();
        ReferenceGrantGenerator grants = new ReferenceGrantGenerator();

        assertFalse(docs.applies(externalCtx));
        assertNull(docs.generate(externalCtx));
        assertFalse(grants.applies(externalCtx));
        assertTrue(route.applies(externalCtx));
        assertFalse(route.generate(externalCtx).contains("\n  host:"));

        ApiService discovery = GeneratorTestSupport.basicService("Discovery", RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME);
        discovery.backends = List.of(backend("local", "http://local.business-apis.svc.cluster.local:8080"));
        ConversionContext discoveryCtx = GeneratorTestSupport.context(discovery, "business-apis", new ConversionOptions());
        assertFalse(route.applies(discoveryCtx));
        assertFalse(docs.applies(discoveryCtx));

        ApiService empty = GeneratorTestSupport.basicService("Empty", "empty");
        ConversionContext emptyCtx = GeneratorTestSupport.context(empty, "business-apis", new ConversionOptions());
        assertFalse(grants.applies(emptyCtx));
        assertFalse(docs.generate(emptyCtx).contains("hostnames:"));

        ResolvedBackend accounts = new ResolvedBackend(
                BackendType.INTERNAL, "accounts", "se", "dr", null, 8080, false, "/", 1,
                "http://accounts.neuralbank-stack.svc.cluster.local:8080");
        ConversionContext duplicates = ConversionContext.build(
                serviceWithIssuer("https://rhbk.apps.example.com/realms/nb"),
                "business-apis", null, new ConversionOptions(), new BackendResolver() {
                    @Override
                    public java.util.List<ResolvedBackend> resolveBackends(
                            ApiService service, String productName, String backendUrl, boolean overrideIgnored) {
                        return java.util.List.of(accounts, accounts);
                    }
                });
        String grant = grants.generate(duplicates);
        assertEqualsOnce(grant, "name: accounts\n");
    }

    private static void assertEqualsOnce(String text, String snippet) {
        int first = text.indexOf(snippet);
        assertTrue(first >= 0);
        assertTrue(text.indexOf(snippet, first + snippet.length()) < 0);
    }

    private static ApiService serviceWithIssuer(String issuer) {
        ApiService service = GeneratorTestSupport.basicService("Neural Bank", "neuralbank");
        Authentication auth = new Authentication();
        auth.oidcIssuerEndpoint = issuer;
        service.authentication = auth;
        return service;
    }

    private static Backend backend(String systemName, String endpoint) {
        Backend backend = new Backend();
        backend.systemName = systemName;
        backend.privateEndpoint = endpoint;
        backend.path = "/";
        return backend;
    }
}
