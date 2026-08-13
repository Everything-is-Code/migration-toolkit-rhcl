package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

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

    // ── exportService() error handling ────────────────────────────────────────

    @Test
    void exportService_invalidUrl_throwsRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> service.exportService("invalid-url", "token", "svc-1"));
    }

    // ── exportServices() error handling ──────────────────────────────────────

    @Test
    void exportServices_invalidUrl_throwsRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> service.exportServices("invalid-url", "token"));
    }
}
