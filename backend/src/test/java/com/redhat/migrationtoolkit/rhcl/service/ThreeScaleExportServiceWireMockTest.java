package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.client.WireMockThreeScaleResource;
import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.dto.ServiceListPage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WireMock integration tests for {@link ThreeScaleExportService}.
 *
 * <p>Covers the paths that cannot be exercised with plain Mockito:
 * <ul>
 *   <li>{@code testConnection()} — exercises {@code buildClient()} → real REST client round-trip</li>
 *   <li>{@code detectVersion()} — exercises {@code java.net.http.HttpClient} header + HTML body paths</li>
 *   <li>{@code listServicesPage()} — exercises {@code buildClient()} + multi-endpoint enrichment round-trip</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(value = WireMockThreeScaleResource.class, restrictToAnnotatedClass = true)
class ThreeScaleExportServiceWireMockTest {

    private static final String ACCESS_TOKEN = "test-token";

    @Inject
    ThreeScaleExportService exportService;

    @BeforeEach
    void setup() {
        WireMockThreeScaleResource.server().resetAll();
        exportService.clearExportCache();
    }

    // ── testConnection() ───────────────────────────────────────────────────────

    @Test
    void testConnection_success_whenServicesRespond_returnsTrue() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/services.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"services":[{"service":{"id":1,"system_name":"echo-api"}}]}
                        """)));

        ConnectionRequest req = new ConnectionRequest();
        req.url = WireMockThreeScaleResource.server().baseUrl();
        req.accessToken = ACCESS_TOKEN;

        assertTrue(exportService.testConnection(req));
    }

    @Test
    void testConnection_whenServicesFail_returnsFalse() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/services.json"))
                .willReturn(aResponse()
                    .withStatus(401)
                    .withBody("Unauthorized")));

        ConnectionRequest req = new ConnectionRequest();
        req.url = WireMockThreeScaleResource.server().baseUrl();
        req.accessToken = ACCESS_TOKEN;

        assertFalse(exportService.testConnection(req));
    }

    // ── detectVersion() ────────────────────────────────────────────────────────

    @Test
    void detectVersion_fromX3scaleVersionHeader_returnsVersion() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/account.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("x-3scale-version", "2.16.0")
                    .withBody("{}")));

        String version = exportService.detectVersion(
            WireMockThreeScaleResource.server().baseUrl(), ACCESS_TOKEN);

        assertEquals("2.16.0", version);
    }

    @Test
    void detectVersion_fromHtmlBody_returnsVersion() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/account.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/p/admin/dashboard"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html")
                    .withBody("""
                        <html><head>
                        <meta name="3scale-version" content="2.14.1">
                        </head><body></body></html>
                        """)));

        String version = exportService.detectVersion(
            WireMockThreeScaleResource.server().baseUrl(), ACCESS_TOKEN);

        assertEquals("2.14.1", version);
    }

    @Test
    void detectVersion_whenUndetectable_returnsNull() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/account.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/p/admin/dashboard"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html")
                    .withBody("<html><body>No version info here</body></html>")));

        String version = exportService.detectVersion(
            WireMockThreeScaleResource.server().baseUrl(), ACCESS_TOKEN);

        assertNull(version);
    }

    // ── listServicesPage() via buildClient URL path ────────────────────────────

    @Test
    void listServicesPage_success_returnsPageWithItems() {
        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/services.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"services":[{"service":{"id":1,"system_name":"echo-api","name":"Echo API"}}]}
                        """)));

        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/backend_apis.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"backend_apis":[]}
                        """)));

        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/services/1/proxy/policies.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"policies_config":[{"name":"cors","version":"built-in","enabled":true,"configuration":{}}]}
                        """)));

        WireMockThreeScaleResource.server().stubFor(
            get(urlPathEqualTo("/admin/api/services/1/backend_usages.json"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

        ServiceListPage page = exportService.listServicesPage(
            WireMockThreeScaleResource.server().baseUrl(), ACCESS_TOKEN, 1, 20);

        assertNotNull(page);
        assertEquals(1, page.items.size());
        assertEquals("1", page.items.get(0).id);
        assertEquals("echo-api", page.items.get(0).systemName);
        assertEquals("Echo API", page.items.get(0).name);
        assertEquals(1, page.page);
        assertEquals(20, page.perPage);
        assertFalse(page.hasMore);
        assertEquals(1, page.total);
        assertNotNull(page.items.get(0).policies);
        assertEquals(1, page.items.get(0).policies.size());
        assertEquals("cors", page.items.get(0).policies.get(0).name);
        assertNotNull(page.items.get(0).backends);

        WireMockThreeScaleResource.server().verify(getRequestedFor(urlPathEqualTo("/admin/api/services.json")));
        WireMockThreeScaleResource.server().verify(getRequestedFor(urlPathEqualTo("/admin/api/backend_apis.json")));
        WireMockThreeScaleResource.server().verify(
                getRequestedFor(urlPathEqualTo("/admin/api/services/1/proxy/policies.json")));
        WireMockThreeScaleResource.server().verify(
                getRequestedFor(urlPathEqualTo("/admin/api/services/1/backend_usages.json")));
    }
}
