package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WU1 threat RED coverage: soft-fail defaults, override precedence, OSSM mapping,
 * sanitized errors, SMCP RBAC soft-path, least-privilege RBAC yaml.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusterVersionServiceTest {

    @Mock
    KubernetesClient client;

    private ClusterVersionService service;

    @BeforeEach
    void setUp() {
        service = new ClusterVersionService(client);
    }

    // ── 1.1 Soft-fail default (Fabric8 403/404) ──────────────────────────────

    @Test
    void resolve_whenClusterVersionForbidden_usesDefaultProfile() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("default", response.source);
        assertTrue(response.ocp.startsWith("4.19"));
        assertEquals("1.2.1", response.gatewayApi);
        assertFalse(response.capabilities.corsNative);
        assertTrue(response.capabilities.timeoutsSupported);
        assertNotNull(response.errors);
        assertFalse(response.errors.isEmpty());
        assertErrorsContainNoSecrets(response.errors);
    }

    @Test
    void resolve_whenClusterVersionNotFound_usesDefaultProfile() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Not Found", HttpURLConnection.HTTP_NOT_FOUND, null));

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("default", response.source);
        assertTrue(response.ocp.startsWith("4.19"));
        assertEquals("1.2.1", response.gatewayApi);
        assertFalse(response.capabilities.corsNative);
        assertErrorsContainNoSecrets(response.errors);
    }

    @Test
    void resolve_whenClientThrowsWithTokenInMessage_sanitizesErrors() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new RuntimeException(
                        "Unauthorized token=sha256~SECRET_TOKEN kubeconfig=/home/user/.kube/config"));

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("default", response.source);
        assertErrorsContainNoSecrets(response.errors);
        String joined = String.join(" ", response.errors);
        assertFalse(joined.contains("SECRET_TOKEN"));
        assertFalse(joined.toLowerCase().contains("kubeconfig"));
        assertFalse(joined.contains("/home/user"));
    }

    @Test
    void resolve_whenClientNull_usesDefaultProfile() {
        ClusterVersionService offline = new ClusterVersionService(null);
        ClusterVersionsResponse response = offline.resolve("auto", true);

        assertEquals("default", response.source);
        assertTrue(response.ocp.startsWith("4.19"));
        assertEquals("1.2.1", response.gatewayApi);
        assertFalse(response.capabilities.corsNative);
    }

    // ── 1.2 Missing SMCP RBAC still resolves via CSV or OCP→OSSM map ─────────

    @Test
    void resolve_whenSmcpForbidden_stillResolvesOssmFromCsv() {
        stubCluster(
                "4.19.10",
                "1.2.1",
                null,
                csv("servicemeshoperator.v2.6.5", "2.6.5"),
                SmcpMode.FORBIDDEN);

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertEquals("2.6.5", response.ossm);
        assertFalse(response.ossm.toLowerCase().contains("istio"));
        assertTrue(response.capabilities.ossmPresent);
        assertEquals("2.6", response.ossmExpectedForOcp);
    }

    @Test
    void resolve_whenSmcpAndCsvUnavailable_usesOcpOssmMap() {
        stubCluster("4.21.0", "1.3.0", null, null, SmcpMode.FORBIDDEN);

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertNotNull(response.ossmExpectedForOcp);
        assertEquals(response.ossmExpectedForOcp, response.ossm);
        assertTrue(response.ocp.startsWith("4.21"));
        assertTrue(response.ossm.startsWith("3."));
    }

    // ── Override precedence ──────────────────────────────────────────────────

    @Test
    void resolve_profile419_overridesLiveDetect() {
        stubCluster("4.21.5", "1.3.0", null, null, SmcpMode.EMPTY);

        ClusterVersionsResponse response = service.resolve("ocp-4.19", true);

        assertEquals("profile", response.source);
        assertEquals("ocp-4.19", response.profile);
        assertTrue(response.ocp.startsWith("4.19"));
        assertEquals("1.2.1", response.gatewayApi);
        assertFalse(response.capabilities.corsNative);
        assertTrue(response.capabilities.timeoutsSupported);
    }

    @Test
    void resolve_profile421_setsCorsNativeTrue() {
        ClusterVersionsResponse response = service.resolve("ocp-4.21", true);

        assertEquals("profile", response.source);
        assertEquals("ocp-4.21", response.profile);
        assertTrue(response.ocp.startsWith("4.21"));
        assertTrue(response.capabilities.corsNative);
        assertTrue(ClusterVersionService.compareVersions(response.gatewayApi, "1.3") >= 0);
        assertNotNull(response.ossmExpectedForOcp);
        assertTrue(response.ossmExpectedForOcp.startsWith("3."));
    }

    // ── Cache refresh ────────────────────────────────────────────────────────

    @Test
    void resolve_refreshFalse_returnsCachedResult() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        ClusterVersionsResponse first = service.resolve("auto", true);
        ClusterVersionsResponse second = service.resolve("auto", false);

        assertSame(first, second);
    }

    @Test
    void resolve_refreshTrue_bypassesCache() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        ClusterVersionsResponse first = service.resolve("auto", true);
        ClusterVersionsResponse second = service.resolve("auto", true);

        assertNotSame(first, second);
        assertEquals(first.source, second.source);
    }

    // ── OSSM mapping ─────────────────────────────────────────────────────────

    @Test
    void expectedOssmForOcp_419_and_421() {
        assertEquals("2.6", ClusterVersionService.expectedOssmForOcp("4.19.10"));
        assertEquals("3.0", ClusterVersionService.expectedOssmForOcp("4.21.0"));
        assertNull(ClusterVersionService.expectedOssmForOcp("4.99.0"));
    }

    @Test
    void resolve_detectedOssmCsv_validatedAgainstMap() {
        stubCluster(
                "4.19.3",
                "1.2.1",
                csv("kuadrant-operator.v1.4.0", "1.4.0"),
                csv("servicemeshoperator.v2.6.5", "2.6.5"),
                SmcpMode.EMPTY);

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertEquals("2.6.5", response.ossm);
        assertEquals("2.6", response.ossmExpectedForOcp);
        assertTrue(response.capabilities.ossmPresent);
        assertTrue(response.capabilities.ossmMatchesOcp);
        assertTrue(response.capabilities.kuadrantPresent);
        assertEquals("1.4.0", response.kuadrant);
        assertFalse(response.capabilities.corsNative);
    }

    // ── 1.7 RBAC least-privilege (get/list only) ─────────────────────────────

    @Test
    void rbacYaml_detectionRulesAreGetListOnly() throws Exception {
        Path file = resolveRbacPath();
        assertTrue(Files.exists(file), "06-rbac.yaml must exist: " + file);

        String yaml = Files.readString(file);
        assertTrue(yaml.contains("clusterversions"), "must grant ClusterVersion reads");
        assertTrue(yaml.contains("customresourcedefinitions"), "must grant CRD reads");
        assertTrue(yaml.contains("clusterserviceversions"), "must grant OLM CSV reads");
        assertTrue(
                yaml.contains("servicemeshcontrolplanes")
                        || yaml.contains("maistra.io")
                        || yaml.contains("sailoperator.io"),
                "must grant OSSM/SMCP reads");

        assertDetectionRuleVerbsAreReadOnly(yaml, "config.openshift.io", "clusterversions");
        assertDetectionRuleVerbsAreReadOnly(yaml, "apiextensions.k8s.io", "customresourcedefinitions");
        assertDetectionRuleVerbsAreReadOnly(yaml, "operators.coreos.com", "clusterserviceversions");
    }

    // ── Capability matrix helpers ────────────────────────────────────────────

    @Test
    void capabilities_fromVersions_corsNativeThreshold() {
        ClusterCapabilities low = ClusterVersionService.capabilitiesFrom(
                "4.19.0", "1.2.1", null, null, "2.6");
        assertFalse(low.corsNative);
        assertTrue(low.timeoutsSupported);

        ClusterCapabilities high = ClusterVersionService.capabilitiesFrom(
                "4.21.0", "1.3.0", "1.4.0", "3.0.1", "3.0");
        assertTrue(high.corsNative);
        assertTrue(high.kuadrantPresent);
        assertTrue(high.ossmPresent);
        assertTrue(high.ossmMatchesOcp);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private enum SmcpMode { EMPTY, FORBIDDEN }

    private record CsvSpec(String name, String version) {}

    private static CsvSpec csv(String name, String version) {
        return new CsvSpec(name, version);
    }

    private static void assertErrorsContainNoSecrets(List<String> errors) {
        assertNotNull(errors);
        for (String err : errors) {
            String lower = err.toLowerCase();
            assertFalse(lower.contains("token="), "error leaked token: " + err);
            assertFalse(lower.contains("bearer "), "error leaked bearer: " + err);
            assertFalse(lower.contains("kubeconfig"), "error leaked kubeconfig: " + err);
            assertFalse(lower.contains("/.kube/"), "error leaked kube path: " + err);
            assertFalse(lower.contains("sha256~"), "error leaked token hash: " + err);
        }
    }

    private static void assertDetectionRuleVerbsAreReadOnly(String yaml, String apiGroup, String resource) {
        String[] rules = yaml.split("- apiGroups:");
        boolean found = false;
        for (String rule : rules) {
            if (rule.contains(apiGroup) && rule.contains(resource)) {
                found = true;
                assertTrue(rule.contains("get") || rule.contains("list"),
                        "rule for " + apiGroup + "/" + resource + " must allow get/list");
                assertFalse(rule.contains("\"create\"") || rule.matches("(?s).*verbs:.*\\bcreate\\b.*"),
                        "detection rule must not allow create: " + rule);
                // Verb list for detection rules should only be get/list
                int verbsIdx = rule.indexOf("verbs:");
                assertTrue(verbsIdx >= 0, "verbs missing for " + apiGroup);
                String verbsSection = rule.substring(verbsIdx);
                // Stop at next top-level key if present
                int next = verbsSection.indexOf("\n  - apiGroups");
                if (next > 0) {
                    verbsSection = verbsSection.substring(0, next);
                }
                assertFalse(verbsSection.contains("create"), "no create");
                assertFalse(verbsSection.contains("update"), "no update");
                assertFalse(verbsSection.contains("patch"), "no patch");
                assertFalse(verbsSection.contains("delete"), "no delete");
                assertFalse(verbsSection.contains("apply"), "no apply");
            }
        }
        assertTrue(found, "no RBAC rule found for " + apiGroup + "/" + resource);
    }

    private static Path resolveRbacPath() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path fromModule = cwd.resolve("../deploy/backend/06-rbac.yaml").normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("deploy/backend/06-rbac.yaml").normalize();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubCluster(
            String ocpVersion,
            String gatewayApiBundle,
            CsvSpec kuadrant,
            CsvSpec ossm,
            SmcpMode smcpMode) {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenAnswer(inv -> {
                    ResourceDefinitionContext ctx = inv.getArgument(0);
                    String plural = ctx.getPlural();
                    MixedOperation op = mock(MixedOperation.class);

                    if ("clusterversions".equals(plural)) {
                        GenericKubernetesResource cv = clusterVersion(ocpVersion);
                        Resource r = mock(Resource.class);
                        when(op.withName("version")).thenReturn(r);
                        when(r.get()).thenReturn(cv);
                        GenericKubernetesResourceList list = new GenericKubernetesResourceList();
                        list.setItems(List.of(cv));
                        when(op.list()).thenReturn(list);
                        return op;
                    }

                    if ("clusterserviceversions".equals(plural)) {
                        NonNamespaceOperation n = mock(NonNamespaceOperation.class);
                        GenericKubernetesResourceList list = new GenericKubernetesResourceList();
                        List<GenericKubernetesResource> items = new ArrayList<>();
                        if (kuadrant != null) {
                            items.add(csvResource(kuadrant.name(), kuadrant.version()));
                        }
                        if (ossm != null) {
                            items.add(csvResource(ossm.name(), ossm.version()));
                        }
                        list.setItems(items);
                        when(op.inAnyNamespace()).thenReturn(n);
                        when(n.list()).thenReturn(list);
                        return op;
                    }

                    if ("servicemeshcontrolplanes".equals(plural)) {
                        if (smcpMode == SmcpMode.FORBIDDEN) {
                            throw new KubernetesClientException(
                                    "Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null);
                        }
                        NonNamespaceOperation n = mock(NonNamespaceOperation.class);
                        GenericKubernetesResourceList list = new GenericKubernetesResourceList();
                        list.setItems(List.of());
                        when(op.inAnyNamespace()).thenReturn(n);
                        when(n.list()).thenReturn(list);
                        return op;
                    }

                    NonNamespaceOperation n = mock(NonNamespaceOperation.class);
                    GenericKubernetesResourceList list = new GenericKubernetesResourceList();
                    list.setItems(List.of());
                    when(op.inAnyNamespace()).thenReturn(n);
                    when(n.list()).thenReturn(list);
                    when(op.list()).thenReturn(list);
                    return op;
                });

        var apiextensions = mock(io.fabric8.kubernetes.client.dsl.ApiextensionsAPIGroupDSL.class);
        var v1 = mock(io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL.class);
        var crdOps = mock(NonNamespaceOperation.class);
        var crdResource = mock(Resource.class);
        CustomResourceDefinition crd = new CustomResourceDefinition();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("gatewayclasses.gateway.networking.k8s.io");
        meta.setAnnotations(Map.of(
                "gateway.networking.k8s.io/bundle-version",
                gatewayApiBundle.startsWith("v") ? gatewayApiBundle : "v" + gatewayApiBundle));
        crd.setMetadata(meta);
        when(client.apiextensions()).thenReturn(apiextensions);
        when(apiextensions.v1()).thenReturn(v1);
        when(v1.customResourceDefinitions()).thenReturn(crdOps);
        when(crdOps.withName("gatewayclasses.gateway.networking.k8s.io")).thenReturn(crdResource);
        when(crdResource.get()).thenReturn(crd);
    }

    private static GenericKubernetesResource clusterVersion(String version) {
        GenericKubernetesResource cv = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("version");
        cv.setMetadata(meta);
        cv.setAdditionalProperty("status", Map.of("desired", Map.of("version", version)));
        return cv;
    }

    private static GenericKubernetesResource csvResource(String name, String version) {
        GenericKubernetesResource csv = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        csv.setMetadata(meta);
        csv.setAdditionalProperty("spec", Map.of("version", version));
        return csv;
    }
}
