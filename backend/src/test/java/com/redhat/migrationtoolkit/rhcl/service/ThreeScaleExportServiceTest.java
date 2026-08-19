package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class ThreeScaleExportServiceTest {

    private ThreeScaleExportService service;

    @BeforeEach
    void setUp() {
        service = new ThreeScaleExportService();
        service.clearExportCache();
    }

    // ── testConnection() ──────────────────────────────────────────────────────

    @Test
    void testConnection_invalidUrl_returnsFalse() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = "not-a-url";
        req.accessToken = "token";
        boolean result = service.testConnection(req);
        assertFalse(result, "Invalid URL should return false");
    }

    @Test
    void testConnection_unreachableHost_returnsFalse() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = "https://nonexistent-3scale-host.example.invalid";
        req.accessToken = "token";
        boolean result = service.testConnection(req);
        assertFalse(result, "Unreachable host should return false");
    }

    @Test
    void testConnection_nullUrl_returnsFalse() {
        ConnectionRequest req = new ConnectionRequest();
        req.url = null;
        req.accessToken = "token";
        assertFalse(service.testConnection(req));
    }

    // ── buildClient() ─────────────────────────────────────────────────────────

    @Test
    void buildClient_invalidUri_throwsRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.buildClient("::invalid::uri::"));
        assertTrue(ex.getMessage().contains("Invalid 3scale URL"));
    }

    // ── extractAuthentication() ───────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "1, apiKey",
        "2, appIdKey",
        "oidc, jwt",
        "3, none",
        ", none"
    })
    void extractAuthentication_backendVersion_mapsCorrectly(String backendVersion, String expectedType) {
        Map<String, Object> svc = new HashMap<>();
        if (backendVersion != null) {
            svc.put("backend_version", backendVersion);
        }
        Authentication auth = service.extractAuthentication(svc);
        assertEquals(expectedType, auth.type);
    }

    // ── extractList() ─────────────────────────────────────────────────────────

    @Test
    void extractList_withValidKey_returnsList() {
        List<Map<String, Object>> data = List.of(Map.of("service", Map.of("id", "1")));
        Map<String, Object> response = Map.of("services", data);
        List<Map<String, Object>> result = service.extractList(response, "services");
        assertEquals(1, result.size());
    }

    @Test
    void extractList_missingKey_returnsEmpty() {
        Map<String, Object> response = Map.of();
        List<Map<String, Object>> result = service.extractList(response, "services");
        assertTrue(result.isEmpty());
    }

    @Test
    void extractList_nonListValue_returnsEmpty() {
        Map<String, Object> response = Map.of("services", "not-a-list");
        List<Map<String, Object>> result = service.extractList(response, "services");
        assertTrue(result.isEmpty());
    }

    // ── fetchApplications() mapping (real Admin API shape) ───────────────────

    @Test
    void applicationDto_fieldAssignment_mapsAppIdAndKeys() {
        Application app = new Application();
        app.id = "99";
        app.appId = "my-app-id";
        app.keys = List.of("my-app-key");
        assertEquals("my-app-id", app.appId);
        assertEquals(List.of("my-app-key"), app.keys);
    }

    @Test
    void fetchApplications_emptyOnClientFailure() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient failing =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(failing.getApplications(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        List<Application> apps = service.fetchApplications(failing, "svc-1", "tok");
        assertTrue(apps.isEmpty());
    }

    @Test
    void fetchApplications_parsesWrappedApplicationAndKeys() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        Map<String, Object> appBody = new HashMap<>();
        appBody.put("id", 42);
        appBody.put("name", "Demo");
        appBody.put("application_id", "real-id");
        appBody.put("service_id", "svc-1");
        Map<String, Object> appsResp = Map.of(
                "applications", List.of(Map.of("application", appBody)));
        org.mockito.Mockito.when(client.getApplications(anyString(), anyInt(), anyInt()))
                .thenReturn(appsResp);
        org.mockito.Mockito.when(client.getApplicationKeys(anyString(), anyString()))
                .thenReturn(Map.of("keys", List.of(Map.of("key", Map.of("value", "real-key")))));

        List<Application> apps = service.fetchApplications(client, "svc-1", "tok");
        assertEquals(1, apps.size());
        assertEquals("real-id", apps.get(0).appId);
        assertEquals(List.of("real-key"), apps.get(0).keys);
    }

    @Test
    void fetchApplications_filtersByServiceId() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        Map<String, Object> match = new HashMap<>();
        match.put("id", 1);
        match.put("name", "Mine");
        match.put("application_id", "a1");
        match.put("service_id", 3);
        Map<String, Object> other = new HashMap<>();
        other.put("id", 2);
        other.put("name", "Other");
        other.put("application_id", "a2");
        other.put("service_id", 99);
        org.mockito.Mockito.when(client.getApplications(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("applications", List.of(
                        Map.of("application", match),
                        Map.of("application", other))));
        org.mockito.Mockito.when(client.getApplicationKeys(anyString(), anyString()))
                .thenReturn(Map.of("keys", List.of()));

        List<Application> apps = service.fetchApplications(client, "3", "tok");
        assertEquals(1, apps.size());
        assertEquals("Mine", apps.get(0).name);
    }

    // ── fetchApplicationPlans() + limits (PR3) ────────────────────────────────

    @Test
    void fetchApplicationPlans_mapsPlansAndLimits() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        Map<String, Object> planBody = new HashMap<>();
        planBody.put("id", 7);
        planBody.put("name", "Basic");
        planBody.put("system_name", "basic");
        Map<String, Object> plansResp = Map.of(
                "plans", List.of(Map.of("application_plan", planBody)));
        org.mockito.Mockito.when(client.getApplicationPlans(anyString(), anyString()))
                .thenReturn(plansResp);

        Map<String, Object> limitBody = new HashMap<>();
        limitBody.put("id", 1);
        limitBody.put("metric_id", 10);
        limitBody.put("metric_system_name", "hits");
        limitBody.put("period", "minute");
        limitBody.put("value", 120);
        Map<String, Object> limitsResp = Map.of(
                "limits", List.of(Map.of("limit", limitBody)));
        org.mockito.Mockito.when(client.getApplicationPlanLimits(anyString(), anyString()))
                .thenReturn(limitsResp);

        List<ApplicationPlan> plans = service.fetchApplicationPlans(client, "svc-1", "tok");
        assertEquals(1, plans.size());
        assertEquals("7", plans.get(0).id);
        assertEquals("Basic", plans.get(0).name);
        assertEquals("basic", plans.get(0).systemName);
        assertNotNull(plans.get(0).limits);
        assertFalse(plans.get(0).limits.isEmpty(), "PR3 must populate plan limits from Admin API");
        Map<String, Object> firstLimit = plans.get(0).limits.get(0);
        assertEquals("minute", firstLimit.get("period"));
        assertEquals(120, ((Number) firstLimit.get("value")).intValue());
    }

    @Test
    void fetchApplicationPlans_emptyOnClientFailure() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient failing =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(failing.getApplicationPlans(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        List<ApplicationPlan> plans = service.fetchApplicationPlans(failing, "svc-1", "tok");
        assertTrue(plans.isEmpty());
    }

    @Test
    void fetchApplicationPlans_limitsFetchFailure_stillReturnsPlanWithEmptyLimits() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        Map<String, Object> planBody = Map.of("id", 3, "name", "Gold", "system_name", "gold");
        org.mockito.Mockito.when(client.getApplicationPlans(anyString(), anyString()))
                .thenReturn(Map.of("application_plans", List.of(Map.of("plan", planBody))));
        org.mockito.Mockito.when(client.getApplicationPlanLimits(anyString(), anyString()))
                .thenThrow(new RuntimeException("limits boom"));

        List<ApplicationPlan> plans = service.fetchApplicationPlans(client, "svc-1", "tok");
        assertEquals(1, plans.size());
        assertEquals("gold", plans.get(0).systemName);
        assertNotNull(plans.get(0).limits);
        assertTrue(plans.get(0).limits.isEmpty(),
                "Failed limits fetch must leave empty limits, not invent values");
    }

    // ── exportService() error handling ────────────────────────────────────────

    @Test
    void exportService_invalidUrl_throwsRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> service.exportService("invalid-url", "token", "svc-1"));
    }

    // ── listServices() / exportServices() error handling ─────────────────────

    @Test
    void listServices_invalidUrl_throwsRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> service.listServices("invalid-url", "token"));
    }

    @Test
    void exportServices_invalidUrl_throwsRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> service.exportServices("invalid-url", "token"));
    }

    // ── listServices(client) list-lite path (P0) ─────────────────────────────

    @Test
    void listServices_paginatesAndSkipsEnrichmentAndDeepFetches() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        // Page 1 full, page 2 partial → stops
        List<Map<String, Object>> page1 = new ArrayList<>();
        for (int i = 0; i < ThreeScaleExportService.LIST_PAGE_SIZE; i++) {
            page1.add(Map.of("service", Map.of(
                    "id", i + 1,
                    "name", "Svc-" + (i + 1),
                    "system_name", "svc_" + (i + 1),
                    "backend_version", "1",
                    "state", "published")));
        }
        Map<String, Object> svc2 = new HashMap<>();
        svc2.put("id", ThreeScaleExportService.LIST_PAGE_SIZE + 1);
        svc2.put("name", "Last");
        svc2.put("system_name", "last");
        svc2.put("backend_version", "2");
        svc2.put("state", "published");
        List<Map<String, Object>> page2 = List.of(Map.of("service", svc2));

        org.mockito.Mockito.when(client.getServices(anyString(), eq(1), eq(ThreeScaleExportService.LIST_PAGE_SIZE)))
                .thenReturn(Map.of("services", page1));
        org.mockito.Mockito.when(client.getServices(anyString(), eq(2), eq(ThreeScaleExportService.LIST_PAGE_SIZE)))
                .thenReturn(Map.of("services", page2));

        List<ApiService> listed = service.listServices(client, "tok");

        assertEquals(ThreeScaleExportService.LIST_PAGE_SIZE + 1, listed.size());
        ApiService first = listed.get(0);
        assertEquals("1", first.id);
        assertEquals("Svc-1", first.name);
        assertEquals("svc_1", first.systemName);
        assertEquals("apiKey", first.authentication.type);
        assertNull(first.policies, "List lite must not enrich policies");
        assertNull(first.backends, "List lite must not enrich backends");
        assertNull(first.mappingRules);
        assertNull(first.metrics);
        assertNull(first.applications);
        assertNull(first.applicationPlans);
        assertNull(first.proxyEndpoint);

        ApiService last = listed.get(listed.size() - 1);
        assertEquals("appIdKey", last.authentication.type);

        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackends(anyString(), anyInt(), anyInt());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getPolicies(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackendUsages(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getApplications(anyString(), anyInt(), anyInt());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getApplicationPlans(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getMappingRules(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getMetrics(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getProxyConfig(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackend(anyString(), anyString());
    }

    @Test
    void listServices_returnsMetadataWithoutCallingPoliciesOrBackends() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        org.mockito.Mockito.when(client.getServices(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("services", List.of(Map.of("service", Map.of(
                        "id", 1, "name", "A", "system_name", "a", "backend_version", "1",
                        "description", "Demo", "state", "published")))));

        List<ApiService> listed = service.listServices(client, "tok");
        assertEquals(1, listed.size());
        assertEquals("A", listed.get(0).name);
        assertEquals("Demo", listed.get(0).description);
        assertEquals("published", listed.get(0).state);
        assertNull(listed.get(0).policies);
        assertNull(listed.get(0).backends);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackends(anyString(), anyInt(), anyInt());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getPolicies(anyString(), anyString());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackendUsages(anyString(), anyString());
    }

    // ── deprecate / backend catalog helpers ───────────────────────────────────

    @Test
    void exportServices_isMarkedDeprecated() throws Exception {
        Method exportServices = ThreeScaleExportService.class
                .getMethod("exportServices", String.class, String.class);
        assertTrue(exportServices.isAnnotationPresent(Deprecated.class),
                "exportServices must carry @Deprecated (S-2)");
    }

    @Test
    void fetchBackendCatalog_onFailure_returnsEmptyCatalog() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("catalog down"));

        Map<String, ?> catalog = service.fetchBackendCatalog(client, "tok");
        assertTrue(catalog.isEmpty(), "Catalog failure must yield empty catalog (S-6 soft path)");
    }

    @Test
    void fetchBackendCatalog_emptyResponse_returnsEmptyCatalog() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("backend_apis", List.of()));

        Map<String, ?> catalog = service.fetchBackendCatalog(client, "tok");
        assertTrue(catalog.isEmpty());
    }

    // ── exportService cache (P0) ──────────────────────────────────────────────

    @Test
    void exportService_secondCallWithinTtl_reusesCachedPayload() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        stubMinimalExport(client, "7", "Cached");

        ApiService first = service.exportService(client, "https://3scale.example", "tok", "7");
        ApiService second = service.exportService(client, "https://3scale.example", "tok", "7");

        assertEquals("Cached", first.name);
        assertSame(first, second, "Within TTL the same instance must be returned");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1))
                .getService(eq("7"), anyString());
    }

    @Test
    void exportService_afterClearCache_fetchesAgain() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        stubMinimalExport(client, "7", "Fresh");

        service.exportService(client, "https://3scale.example", "tok", "7");
        service.clearExportCache();
        ApiService again = service.exportService(client, "https://3scale.example", "tok", "7");

        assertEquals("Fresh", again.name);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(2))
                .getService(eq("7"), anyString());
    }

    @Test
    void exportService_differentServiceIds_doNotShareCache() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        stubMinimalExport(client, "1", "One");
        stubMinimalExport(client, "2", "Two");

        ApiService one = service.exportService(client, "https://3scale.example", "tok", "1");
        ApiService two = service.exportService(client, "https://3scale.example", "tok", "2");

        assertEquals("One", one.name);
        assertEquals("Two", two.name);
        org.mockito.Mockito.verify(client).getService(eq("1"), anyString());
        org.mockito.Mockito.verify(client).getService(eq("2"), anyString());
    }

    private static void stubMinimalExport(
            com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client,
            String serviceId,
            String name) {
        org.mockito.Mockito.when(client.getService(eq(serviceId), anyString()))
                .thenReturn(Map.of("service", Map.of(
                        "id", Integer.parseInt(serviceId),
                        "name", name,
                        "system_name", "sys_" + serviceId,
                        "backend_version", "1")));
        org.mockito.Mockito.when(client.getPolicies(eq(serviceId), anyString()))
                .thenReturn(Map.of("policies_config", List.of()));
        org.mockito.Mockito.when(client.getMappingRules(eq(serviceId), anyString()))
                .thenReturn(Map.of("mapping_rules", List.of()));
        org.mockito.Mockito.when(client.getMetrics(eq(serviceId), anyString()))
                .thenReturn(Map.of("metrics", List.of()));
        org.mockito.Mockito.when(client.getBackendUsages(eq(serviceId), anyString()))
                .thenReturn(List.of());
        org.mockito.Mockito.when(client.getApplications(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("applications", List.of()));
        org.mockito.Mockito.when(client.getApplicationPlans(eq(serviceId), anyString()))
                .thenReturn(Map.of("plans", List.of()));
    }

    // ── Multi-backend path/weight capture (#28) ───────────────────────────────

    @Test
    void resolveBackendsFromUsages_capturesPathAndWeightOnClone_doesNotMutateCatalog() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        Backend catalogEntry = new Backend();
        catalogEntry.id = "10";
        catalogEntry.name = "Orders";
        catalogEntry.systemName = "orders";
        catalogEntry.privateEndpoint = "https://orders.example.com";
        Map<String, Backend> catalog = new HashMap<>();
        catalog.put("10", catalogEntry);

        Map<String, Object> usageA = new HashMap<>();
        usageA.put("backend_id", 10);
        usageA.put("path", "/orders");
        usageA.put("weight", 2);
        Map<String, Object> usageB = new HashMap<>();
        usageB.put("backend_id", 10);
        usageB.put("path", "/legacy");
        usageB.put("weight", 1);
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(
                        Map.of("backend_usage", usageA),
                        Map.of("backend_usage", usageB)));

        List<Backend> resolved = service.resolveBackendsFromUsages(client, "svc-1", "tok", catalog);

        assertEquals(2, resolved.size());
        assertEquals("/orders", resolved.get(0).path);
        assertEquals(2, resolved.get(0).weight);
        assertEquals("/legacy", resolved.get(1).path);
        assertEquals(1, resolved.get(1).weight);
        assertNull(catalogEntry.path, "Catalog entry must not receive usage path");
        assertNull(catalogEntry.weight, "Catalog entry must not receive usage weight");
        assertNotSame(catalogEntry, resolved.get(0));
        assertNotSame(catalogEntry, resolved.get(1));
    }

    @Test
    void resolveBackendsFromUsages_blankPath_normalizesToRoot() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        Backend catalogEntry = new Backend();
        catalogEntry.id = "5";
        catalogEntry.name = "Root";
        catalogEntry.systemName = "root";
        Map<String, Backend> catalog = Map.of("5", catalogEntry);

        Map<String, Object> usage = new HashMap<>();
        usage.put("backend_id", 5);
        usage.put("path", "  ");
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(Map.of("backend_usage", usage)));

        List<Backend> resolved = service.resolveBackendsFromUsages(client, "svc-1", "tok", catalog);
        assertEquals(1, resolved.size());
        assertEquals("/", resolved.get(0).path);
        assertNull(catalogEntry.path);
    }

    @Test
    void resolveBackendsFromUsages_fallbackGet_attachesUsagePathWithoutCatalogMutation() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(Map.of("backend_usage", Map.of(
                        "backend_id", 7, "path", "/payments", "weight", 3))));
        org.mockito.Mockito.when(client.getBackend(eq("7"), anyString()))
                .thenReturn(Map.of("backend_api", Map.of(
                        "id", 7, "name", "Pay", "system_name", "pay",
                        "private_endpoint", "https://pay.example.com")));

        List<Backend> resolved = service.resolveBackendsFromUsages(
                client, "svc-1", "tok", Map.of());

        assertEquals(1, resolved.size());
        assertEquals("/payments", resolved.get(0).path);
        assertEquals(3, resolved.get(0).weight);
        assertEquals("Pay", resolved.get(0).name);
    }
}
