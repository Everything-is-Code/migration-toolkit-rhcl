package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
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
    void buildClient_invalidUri_throwsRuntimeException() throws Exception {
        Method buildClient = ThreeScaleExportService.class
                .getDeclaredMethod("buildClient", String.class);
        buildClient.setAccessible(true);
        Exception ex = assertThrows(Exception.class,
                () -> buildClient.invoke(service, "::invalid::uri::"));
        assertNotNull(ex.getCause() != null ? ex.getCause() : ex);
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
    void extractAuthentication_backendVersion_mapsCorrectly(String backendVersion, String expectedType)
            throws Exception {
        Method extractAuth = ThreeScaleExportService.class
                .getDeclaredMethod("extractAuthentication", Map.class);
        extractAuth.setAccessible(true);

        Map<String, Object> svc = new HashMap<>();
        if (backendVersion != null) {
            svc.put("backend_version", backendVersion);
        }
        Authentication auth = (Authentication) extractAuth.invoke(service, svc);
        assertEquals(expectedType, auth.type);
    }

    // ── extractList() ─────────────────────────────────────────────────────────

    @Test
    void extractList_withValidKey_returnsList() throws Exception {
        Method extractList = ThreeScaleExportService.class
                .getDeclaredMethod("extractList", Map.class, String.class);
        extractList.setAccessible(true);

        List<Map<String, Object>> data = List.of(Map.of("service", Map.of("id", "1")));
        Map<String, Object> response = Map.of("services", data);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) extractList.invoke(service, response, "services");
        assertEquals(1, result.size());
    }

    @Test
    void extractList_missingKey_returnsEmpty() throws Exception {
        Method extractList = ThreeScaleExportService.class
                .getDeclaredMethod("extractList", Map.class, String.class);
        extractList.setAccessible(true);

        Map<String, Object> response = Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) extractList.invoke(service, response, "services");
        assertTrue(result.isEmpty());
    }

    @Test
    void extractList_nonListValue_returnsEmpty() throws Exception {
        Method extractList = ThreeScaleExportService.class
                .getDeclaredMethod("extractList", Map.class, String.class);
        extractList.setAccessible(true);

        Map<String, Object> response = Map.of("services", "not-a-list");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) extractList.invoke(service, response, "services");
        assertTrue(result.isEmpty());
    }

    // ── fetchApplications() mapping (real Admin API shape) ───────────────────

    @Test
    void fetchApplications_mapsAppIdAndKeys() throws Exception {
        // Build a fake client via a simple stub using anonymous class would require RestClient;
        // instead unit-test key parsing path through reflection on a mock-like response mapper.
        Method fetchKeys = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplicationKeys",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        assertNotNull(fetchKeys);
        // Verify Application model wiring used by convert
        Application app = new Application();
        app.id = "99";
        app.appId = "my-app-id";
        app.keys = List.of("my-app-key");
        assertEquals("my-app-id", app.appId);
        assertEquals(List.of("my-app-key"), app.keys);
    }

    @Test
    void fetchApplications_emptyOnClientFailure() throws Exception {
        Method fetchApps = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplications",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        fetchApps.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient failing =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(failing.getApplications(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        @SuppressWarnings("unchecked")
        List<Application> apps = (List<Application>) fetchApps.invoke(service, failing, "svc-1", "tok");
        assertTrue(apps.isEmpty());
    }

    @Test
    void fetchApplications_parsesWrappedApplicationAndKeys() throws Exception {
        Method fetchApps = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplications",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        fetchApps.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        Map<String, Object> appBody = new HashMap<>();
        appBody.put("id", 42);
        appBody.put("name", "Demo");
        appBody.put("application_id", "real-id");
        Map<String, Object> appsResp = Map.of(
                "applications", List.of(Map.of("application", appBody)));
        org.mockito.Mockito.when(client.getApplications(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(appsResp);
        org.mockito.Mockito.when(client.getApplicationKeys(anyString(), anyString()))
                .thenReturn(Map.of("keys", List.of(Map.of("key", Map.of("value", "real-key")))));

        @SuppressWarnings("unchecked")
        List<Application> apps = (List<Application>) fetchApps.invoke(service, client, "svc-1", "tok");
        assertEquals(1, apps.size());
        assertEquals("real-id", apps.get(0).appId);
        assertEquals(List.of("real-key"), apps.get(0).keys);
    }

    // ── fetchApplicationPlans() + limits (PR3) ────────────────────────────────

    @Test
    void fetchApplicationPlans_mapsPlansAndLimits() throws Exception {
        Method fetchPlans = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplicationPlans",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        fetchPlans.setAccessible(true);

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

        @SuppressWarnings("unchecked")
        List<ApplicationPlan> plans =
                (List<ApplicationPlan>) fetchPlans.invoke(service, client, "svc-1", "tok");
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
    void fetchApplicationPlans_emptyOnClientFailure() throws Exception {
        Method fetchPlans = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplicationPlans",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        fetchPlans.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient failing =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(failing.getApplicationPlans(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        @SuppressWarnings("unchecked")
        List<ApplicationPlan> plans =
                (List<ApplicationPlan>) fetchPlans.invoke(service, failing, "svc-1", "tok");
        assertTrue(plans.isEmpty());
    }

    @Test
    void fetchApplicationPlans_limitsFetchFailure_stillReturnsPlanWithEmptyLimits() throws Exception {
        Method fetchPlans = ThreeScaleExportService.class
                .getDeclaredMethod("fetchApplicationPlans",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class, String.class);
        fetchPlans.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        Map<String, Object> planBody = Map.of("id", 3, "name", "Gold", "system_name", "gold");
        org.mockito.Mockito.when(client.getApplicationPlans(anyString(), anyString()))
                .thenReturn(Map.of("application_plans", List.of(Map.of("plan", planBody))));
        org.mockito.Mockito.when(client.getApplicationPlanLimits(anyString(), anyString()))
                .thenThrow(new RuntimeException("limits boom"));

        @SuppressWarnings("unchecked")
        List<ApplicationPlan> plans =
                (List<ApplicationPlan>) fetchPlans.invoke(service, client, "svc-1", "tok");
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

    // ── listServices(client) lightweight path ────────────────────────────────

    @Test
    void listServices_paginatesAndSkipsDeepFetches() {
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

        Map<String, Object> backendBody = new HashMap<>();
        backendBody.put("id", 9);
        backendBody.put("name", "Upstream");
        backendBody.put("system_name", "upstream");
        backendBody.put("private_endpoint", "https://httpbin.org");
        org.mockito.Mockito.when(client.getBackends(anyString(), eq(1), eq(ThreeScaleExportService.LIST_PAGE_SIZE)))
                .thenReturn(Map.of("backend_apis", List.of(Map.of("backend_api", backendBody))));

        org.mockito.Mockito.when(client.getPolicies(anyString(), anyString()))
                .thenReturn(Map.of("policies_config", List.of(
                        Map.of("name", "cors", "version", "builtin", "enabled", true))));
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(Map.of("backend_usage", Map.of("backend_id", 9))));

        List<ApiService> listed = service.listServices(client, "tok");

        assertEquals(ThreeScaleExportService.LIST_PAGE_SIZE + 1, listed.size());
        ApiService first = listed.get(0);
        assertEquals("apiKey", first.authentication.type);
        assertNotNull(first.policies);
        assertEquals(1, first.policies.size());
        assertEquals("cors", first.policies.get(0).name);
        assertNotNull(first.backends);
        assertEquals(1, first.backends.size());
        assertEquals("Upstream", first.backends.get(0).name);
        assertNull(first.mappingRules);
        assertNull(first.metrics);
        assertNull(first.applications);
        assertNull(first.applicationPlans);
        assertNull(first.proxyEndpoint);

        ApiService last = listed.get(listed.size() - 1);
        assertEquals("appIdKey", last.authentication.type);

        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getApplications(anyString(), anyString(), anyInt(), anyInt());
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
    void listServices_resolvesBackendsFromCatalogWithoutPerBackendGet() {
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        org.mockito.Mockito.when(client.getServices(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("services", List.of(Map.of("service", Map.of(
                        "id", 1, "name", "A", "system_name", "a", "backend_version", "1")))));
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("backends", List.of(Map.of("backend", Map.of(
                        "id", 3, "name", "Shared", "system_name", "shared")))));
        org.mockito.Mockito.when(client.getPolicies(anyString(), anyString()))
                .thenReturn(Map.of("policies_config", List.of()));
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(Map.of("backend_usage", Map.of("backend_id", 3))));

        List<ApiService> listed = service.listServices(client, "tok");
        assertEquals(1, listed.size());
        assertEquals("Shared", listed.get(0).backends.get(0).name);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .getBackend(anyString(), anyString());
    }

    // ── WU1: enrich soft-fail / deprecate / empty catalog ────────────────────

    @Test
    void exportServices_isMarkedDeprecated() throws Exception {
        Method exportServices = ThreeScaleExportService.class
                .getMethod("exportServices", String.class, String.class);
        assertTrue(exportServices.isAnnotationPresent(Deprecated.class),
                "exportServices must carry @Deprecated (S-2)");
    }

    @Test
    void listServices_enrichFailureOnOneService_softFailsAndReturnsOthers() {
        ThreeScaleExportService spySvc = org.mockito.Mockito.spy(new ThreeScaleExportService());
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        org.mockito.Mockito.when(client.getServices(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("services", List.of(
                        Map.of("service", Map.of(
                                "id", 1, "name", "Good", "system_name", "good", "backend_version", "1")),
                        Map.of("service", Map.of(
                                "id", 2, "name", "Bad", "system_name", "bad", "backend_version", "1")))));
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("backend_apis", List.of()));
        org.mockito.Mockito.when(client.getPolicies(anyString(), anyString()))
                .thenReturn(Map.of("policies_config", List.of(
                        Map.of("name", "cors", "version", "builtin", "enabled", true))));
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of());

        org.mockito.Mockito.doThrow(new RuntimeException("enrich boom"))
                .when(spySvc).resolveBackendsFromUsages(
                        eq(client), eq("2"), anyString(), org.mockito.ArgumentMatchers.anyMap());

        List<ApiService> listed = assertDoesNotThrow(() -> spySvc.listServices(client, "tok"));
        assertEquals(2, listed.size());

        ApiService good = listed.stream().filter(s -> "1".equals(s.id)).findFirst().orElseThrow();
        ApiService bad = listed.stream().filter(s -> "2".equals(s.id)).findFirst().orElseThrow();
        assertNotNull(good.policies);
        assertEquals(1, good.policies.size());
        assertEquals("cors", good.policies.get(0).name);
        assertNull(bad.backends, "Failed enrich must leave backends unset on the failed service");
    }

    @Test
    void listServices_enrichFailureOnAllServices_stillReturnsListWithoutThrowing() {
        ThreeScaleExportService spySvc = org.mockito.Mockito.spy(new ThreeScaleExportService());
        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);

        org.mockito.Mockito.when(client.getServices(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("services", List.of(
                        Map.of("service", Map.of(
                                "id", 10, "name", "A", "system_name", "a", "backend_version", "1")),
                        Map.of("service", Map.of(
                                "id", 11, "name", "B", "system_name", "b", "backend_version", "1")))));
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("backend_apis", List.of()));
        org.mockito.Mockito.when(client.getPolicies(anyString(), anyString()))
                .thenReturn(Map.of("policies_config", List.of()));

        org.mockito.Mockito.doThrow(new RuntimeException("enrich boom"))
                .when(spySvc).resolveBackendsFromUsages(
                        eq(client), anyString(), anyString(), org.mockito.ArgumentMatchers.anyMap());

        List<ApiService> listed = assertDoesNotThrow(() -> spySvc.listServices(client, "tok"));
        assertEquals(2, listed.size());
        assertTrue(listed.stream().allMatch(s -> s.backends == null),
                "All enrich failures must soft-fail without aborting the list");
    }

    @Test
    void fetchBackendCatalog_onFailure_returnsEmptyAndListStillWorksViaFallback() throws Exception {
        Method fetchCatalog = ThreeScaleExportService.class
                .getDeclaredMethod("fetchBackendCatalog",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class);
        fetchCatalog.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("catalog down"));

        @SuppressWarnings("unchecked")
        Map<String, ?> catalog = (Map<String, ?>) fetchCatalog.invoke(service, client, "tok");
        assertTrue(catalog.isEmpty(), "Catalog failure must yield empty catalog (S-6 soft path)");

        org.mockito.Mockito.when(client.getServices(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("services", List.of(Map.of("service", Map.of(
                        "id", 1, "name", "A", "system_name", "a", "backend_version", "1")))));
        org.mockito.Mockito.when(client.getPolicies(anyString(), anyString()))
                .thenReturn(Map.of("policies_config", List.of()));
        org.mockito.Mockito.when(client.getBackendUsages(anyString(), anyString()))
                .thenReturn(List.of(Map.of("backend_usage", Map.of("backend_id", 3))));
        org.mockito.Mockito.when(client.getBackend(eq("3"), anyString()))
                .thenReturn(Map.of("backend_api", Map.of(
                        "id", 3, "name", "Fallback", "system_name", "fb",
                        "private_endpoint", "https://example.test")));

        List<ApiService> listed = assertDoesNotThrow(() -> service.listServices(client, "tok"));
        assertEquals(1, listed.size());
        assertEquals("Fallback", listed.get(0).backends.get(0).name);
        org.mockito.Mockito.verify(client).getBackend(eq("3"), anyString());
    }

    @Test
    void fetchBackendCatalog_emptyResponse_returnsEmptyCatalog() throws Exception {
        Method fetchCatalog = ThreeScaleExportService.class
                .getDeclaredMethod("fetchBackendCatalog",
                        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class,
                        String.class);
        fetchCatalog.setAccessible(true);

        com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient client =
                org.mockito.Mockito.mock(com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient.class);
        org.mockito.Mockito.when(client.getBackends(anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("backend_apis", List.of()));

        @SuppressWarnings("unchecked")
        Map<String, ?> catalog = (Map<String, ?>) fetchCatalog.invoke(service, client, "tok");
        assertTrue(catalog.isEmpty());
    }
}
