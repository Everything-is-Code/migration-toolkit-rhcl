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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        // Same-thread executor: Mockito Fabric8 mocks are not safe across FJP classloaders.
        service.useDetectExecutor(Runnable::run);
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
        assertSoftFailOssmNullButExpected(response);
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
        assertSoftFailOssmNullButExpected(response);
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
        offline.useDetectExecutor(Runnable::run);
        ClusterVersionsResponse response = offline.resolve("auto", true);

        assertEquals("default", response.source);
        assertTrue(response.ocp.startsWith("4.19"));
        assertEquals("1.2.1", response.gatewayApi);
        assertFalse(response.capabilities.corsNative);
        assertFalse(response.capabilities.clusterReachable);
        assertSoftFailOssmNullButExpected(response);
    }

    @Test
    void softFailDefault_ossmNullAndNotPresent_whileExpectedKept() {
        ClusterVersionService offline = new ClusterVersionService(null);
        offline.useDetectExecutor(Runnable::run);
        ClusterVersionsResponse response = offline.resolve("auto", true);

        assertEquals("default", response.source);
        assertNull(response.ossm);
        assertFalse(response.capabilities.ossmPresent);
        assertEquals("2.6", response.ossmExpectedForOcp);
        assertFalse(response.capabilities.ossmMatchesOcp);
        assertFalse(response.capabilities.clusterReachable);
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
        assertTrue(response.capabilities.clusterReachable);
        assertEquals("2.6", response.ossmExpectedForOcp);
        assertTrue(response.capabilities.ossmMatchesOcp);
        // CSV already supplied OSSM — SMCP must not be consulted / must not warn
        assertTrue(response.errors == null || response.errors.stream()
                .noneMatch(e -> e.toLowerCase().contains("smcp")));
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
        assertTrue(response.capabilities.clusterReachable);
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
        assertTrue(response.capabilities.clusterReachable);
    }

    // ── Cache refresh + TTL ──────────────────────────────────────────────────

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

    @Test
    void resolve_withinTtl_returnsCachedResult() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        MutableClockService clocked = new MutableClockService(client, 1_000_000L);
        ClusterVersionsResponse first = clocked.resolve("auto", true);
        clocked.now = 1_000_000L + (4 * 60 * 1000L); // still within 5m
        ClusterVersionsResponse second = clocked.resolve("auto", false);

        assertSame(first, second);
    }

    @Test
    void resolve_afterTtlExpires_reResolves() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        MutableClockService clocked = new MutableClockService(client, 1_000_000L);
        ClusterVersionsResponse first = clocked.resolve("auto", true);
        clocked.now = 1_000_000L + (5 * 60 * 1000L); // exactly at TTL boundary → stale
        ClusterVersionsResponse second = clocked.resolve("auto", false);

        assertNotSame(first, second);
        assertEquals(first.source, second.source);
    }

    @Test
    void resolve_refreshTrue_bypassesEvenWithinTtl() {
        when(client.genericKubernetesResources(any(ResourceDefinitionContext.class)))
                .thenThrow(new KubernetesClientException("Forbidden", HttpURLConnection.HTTP_FORBIDDEN, null));

        MutableClockService clocked = new MutableClockService(client, 1_000_000L);
        ClusterVersionsResponse first = clocked.resolve("auto", true);
        clocked.now = 1_000_000L + 1_000L;
        ClusterVersionsResponse second = clocked.resolve("auto", true);

        assertNotSame(first, second);
    }

    // ── CSV listed once per detect ───────────────────────────────────────────

    @Test
    void resolve_listsClusterServiceVersionsOnce() {
        stubCluster(
                "4.19.3",
                "1.2.1",
                csv("kuadrant-operator.v1.4.0", "1.4.0"),
                csv("servicemeshoperator.v2.6.5", "2.6.5"),
                SmcpMode.EMPTY);

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertEquals("1.4.0", response.kuadrant);
        assertEquals("2.6.5", response.ossm);
        // One ResourceDefinitionContext for CSVs; namespaces are probed via inNamespace().
        verify(client, times(1)).genericKubernetesResources(argThat(
                ctx -> ctx != null && "clusterserviceversions".equals(ctx.getPlural())));
    }

    @Test
    void resolve_whenOnlyAuthPolicyCrd_marksKuadrantPresent() {
        stubCluster("4.19.3", "1.2.1", List.of(), SmcpMode.EMPTY);
        // Override CRD stub: AuthPolicy present without CSV.
        var apiextensions = mock(io.fabric8.kubernetes.client.dsl.ApiextensionsAPIGroupDSL.class);
        var v1 = mock(io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL.class);
        var crdOps = mock(NonNamespaceOperation.class);
        when(client.apiextensions()).thenReturn(apiextensions);
        when(apiextensions.v1()).thenReturn(v1);
        when(v1.customResourceDefinitions()).thenReturn(crdOps);
        when(crdOps.withName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            Resource resource = mock(Resource.class);
            if ("gatewayclasses.gateway.networking.k8s.io".equals(name)) {
                CustomResourceDefinition gatewayCrd = new CustomResourceDefinition();
                ObjectMeta meta = new ObjectMeta();
                meta.setName(name);
                meta.setAnnotations(Map.of("gateway.networking.k8s.io/bundle-version", "v1.2.1"));
                gatewayCrd.setMetadata(meta);
                when(resource.get()).thenReturn(gatewayCrd);
            } else if ("authpolicies.kuadrant.io".equals(name)) {
                CustomResourceDefinition auth = new CustomResourceDefinition();
                ObjectMeta meta = new ObjectMeta();
                meta.setName(name);
                auth.setMetadata(meta);
                when(resource.get()).thenReturn(auth);
            } else {
                when(resource.get()).thenReturn(null);
            }
            return resource;
        });

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertEquals(ClusterVersionService.KUADRANT_PRESENT_VIA_CRD, response.kuadrant);
        assertTrue(response.capabilities.kuadrantPresent);
    }

    @Test
    void awaitDetect_whenFutureExceedsTimeout_softFailsWithTimeoutError() {
        ClusterVersionService fastTimeout = new ClusterVersionService(client) {
            @Override
            long detectTimeoutSeconds() {
                return 1L;
            }
        };
        CompletableFuture<ClusterVersionsResponse> never = new CompletableFuture<>();

        long started = System.currentTimeMillis();
        ClusterVersionsResponse response = fastTimeout.awaitDetect(never, "auto");
        long elapsedMs = System.currentTimeMillis() - started;

        assertEquals("default", response.source);
        assertEquals(ClusterVersionService.DEFAULT_OCP, response.ocp);
        assertEquals(ClusterVersionService.DEFAULT_GATEWAY_API, response.gatewayApi);
        assertNull(response.kuadrant);
        assertNull(response.ossm);
        assertNotNull(response.errors);
        assertTrue(response.errors.stream().anyMatch(e -> e.contains("timed out")),
                "Timeout soft-fail must surface a timeout error note: " + response.errors);
        assertTrue(elapsedMs < 5_000L,
                "Soft-fail path must not wait the production DETECT_TIMEOUT_SECONDS ceiling");
        assertFalse(response.capabilities.clusterReachable);
        // orTimeout completes the same future exceptionally when the ceiling elapses.
        assertTrue(never.isCompletedExceptionally());
    }

    @Test
    void resolve_concurrentCalls_coalesceIntoSingleDetect() throws Exception {
        AtomicInteger detects = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ClusterVersionService coalescing = new ClusterVersionService(null) {
            @Override
            ClusterVersionsResponse runDetect(String profile) {
                detects.incrementAndGet();
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("release latch timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                ClusterVersionsResponse r = new ClusterVersionsResponse();
                r.source = "default";
                r.profile = "auto";
                r.ocp = DEFAULT_OCP;
                r.gatewayApi = DEFAULT_GATEWAY_API;
                r.capabilities = new ClusterCapabilities();
                return r;
            }
        };
        // Real async executor so the first resolve stays in-flight while the second joins.
        coalescing.useDetectExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "coalesce-test");
            t.setDaemon(true);
            return t;
        }));

        CompletableFuture<ClusterVersionsResponse> first =
                CompletableFuture.supplyAsync(() -> coalescing.resolve("auto", true));
        assertTrue(entered.await(5, TimeUnit.SECONDS), "detect must start");
        CompletableFuture<ClusterVersionsResponse> second =
                CompletableFuture.supplyAsync(() -> coalescing.resolve("auto", true));
        // Give the second resolve a moment to hit inFlight.compute while first is blocked.
        Thread.sleep(100);
        release.countDown();

        assertEquals("default", first.get(5, TimeUnit.SECONDS).source);
        assertEquals("default", second.get(5, TimeUnit.SECONDS).source);
        assertEquals(1, detects.get(), "Concurrent resolve must coalesce to one detect");
    }

    @Test
    void awaitDetect_whenFutureFails_softFailsWithFailureError() {
        CompletableFuture<ClusterVersionsResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom-detect"));

        ClusterVersionsResponse response = service.awaitDetect(failed, "auto");

        assertEquals("default", response.source);
        assertTrue(response.errors.stream().anyMatch(e -> e.contains("Cluster detect failed")),
                "Non-timeout failures must soft-fail with a detect-failed note: " + response.errors);
        assertFalse(response.errors.stream().anyMatch(e -> e.contains("timed out")));
        assertFalse(response.capabilities.clusterReachable);
    }

    // ── I-7 unbounded CSV list residual ──────────────────────────────────────

    @Test
    void warnIfUnboundedCsvListLarge_thresholdBoundary() {
        assertEquals(500, ClusterVersionService.CSV_LIST_SIZE_WARN_THRESHOLD);
        // Below threshold: no throw; warn path is a no-op (LOG not asserted here).
        ClusterVersionService.warnIfUnboundedCsvListLarge(499);
        // At/above threshold: WARN path executes without error.
        ClusterVersionService.warnIfUnboundedCsvListLarge(500);
        ClusterVersionService.warnIfUnboundedCsvListLarge(501);
    }

    @Test
    void resolve_whenCsvListAtLeast500_stillDetectsOperators() {
        List<CsvSpec> csvs = new ArrayList<>(ClusterVersionService.CSV_LIST_SIZE_WARN_THRESHOLD);
        for (int i = 0; i < ClusterVersionService.CSV_LIST_SIZE_WARN_THRESHOLD - 2; i++) {
            csvs.add(csv("filler-operator-" + i + ".v1.0.0", "1.0.0"));
        }
        csvs.add(csv("kuadrant-operator.v1.4.0", "1.4.0"));
        csvs.add(csv("servicemeshoperator.v2.6.5", "2.6.5"));
        assertEquals(ClusterVersionService.CSV_LIST_SIZE_WARN_THRESHOLD, csvs.size());

        stubCluster("4.19.3", "1.2.1", csvs, SmcpMode.EMPTY);

        ClusterVersionsResponse response = service.resolve("auto", true);

        assertEquals("detected", response.source);
        assertEquals("1.4.0", response.kuadrant);
        assertEquals("2.6.5", response.ossm);
        // listCsvs invokes warnIfUnboundedCsvListLarge when size >= threshold (I-7).
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
        assertTrue(low.retriesSupported,
                "GAPI 1.2.1 / OCP 4.19 must support HTTPRoute retry.attempts");

        ClusterCapabilities high = ClusterVersionService.capabilitiesFrom(
                "4.21.0", "1.3.0", "1.4.0", "3.0.1", "3.0");
        assertTrue(high.corsNative);
        assertTrue(high.kuadrantPresent);
        assertTrue(high.ossmPresent);
        assertTrue(high.ossmMatchesOcp);
        assertTrue(high.retriesSupported);

        // OSSM newer than the OCP minimum (e.g. 3.4.1 vs expected 3.0) still matches
        ClusterCapabilities newer = ClusterVersionService.capabilitiesFrom(
                "4.21.27", "1.3.0", "1.4.2", "3.4.1", "3.0");
        assertTrue(newer.ossmMatchesOcp);

        // Below the minimum does not match
        ClusterCapabilities older = ClusterVersionService.capabilitiesFrom(
                "4.21.0", "1.3.0", "1.4.0", "2.6.5", "3.0");
        assertFalse(older.ossmMatchesOcp);

        ClusterCapabilities preRetry = ClusterVersionService.capabilitiesFrom(
                "4.17.0", "1.1.0", null, null, null);
        assertFalse(preRetry.retriesSupported,
                "GAPI < 1.2 and OCP < 4.19 must not claim retriesSupported");
    }

    @Test
    void ossmCompatibilityTable_returnsOcpOssmMatrix() {
        Map<String, String> table = service.ossmCompatibilityTable();
        assertFalse(table.isEmpty());
        assertEquals("2.6", table.get("4.19"));
        assertEquals("3.0", table.get("4.21"));
    }

    @Test
    void sanitize_redactsSecretsAndHomePaths() {
        String cleaned = ClusterVersionService.sanitize("access_token=secret123 /Users/me/cluster");
        assertFalse(cleaned.contains("secret123"));
        assertTrue(cleaned.contains("[redacted]"));
        assertTrue(cleaned.contains("[redacted-path]"));
    }

    @Test
    void sanitize_nullMessage_returnsGenericError() {
        assertEquals("detection error", ClusterVersionService.sanitize(null));
    }

    @Test
    void compareVersions_equalAndGreater() {
        assertEquals(0, ClusterVersionService.compareVersions("1.2.3", "1.2.3"));
        assertTrue(ClusterVersionService.compareVersions("2.0.0", "1.99.0") > 0);
        assertEquals(0, ClusterVersionService.compareVersions(null, null));
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

    private void stubCluster(
            String ocpVersion,
            String gatewayApiBundle,
            CsvSpec kuadrant,
            CsvSpec ossm,
            SmcpMode smcpMode) {
        List<CsvSpec> csvs = new ArrayList<>(2);
        if (kuadrant != null) {
            csvs.add(kuadrant);
        }
        if (ossm != null) {
            csvs.add(ossm);
        }
        stubCluster(ocpVersion, gatewayApiBundle, csvs, smcpMode);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubCluster(
            String ocpVersion,
            String gatewayApiBundle,
            List<CsvSpec> csvSpecs,
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
                        if (csvSpecs != null) {
                            for (CsvSpec spec : csvSpecs) {
                                items.add(csvResource(spec.name(), spec.version()));
                            }
                        }
                        list.setItems(items);
                        when(op.inAnyNamespace()).thenReturn(n);
                        when(op.inNamespace(anyString())).thenReturn(n);
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
        CustomResourceDefinition gatewayCrd = new CustomResourceDefinition();
        ObjectMeta gatewayMeta = new ObjectMeta();
        gatewayMeta.setName("gatewayclasses.gateway.networking.k8s.io");
        gatewayMeta.setAnnotations(Map.of(
                "gateway.networking.k8s.io/bundle-version",
                gatewayApiBundle.startsWith("v") ? gatewayApiBundle : "v" + gatewayApiBundle));
        gatewayCrd.setMetadata(gatewayMeta);

        CustomResourceDefinition authPolicyCrd = new CustomResourceDefinition();
        ObjectMeta authMeta = new ObjectMeta();
        authMeta.setName("authpolicies.kuadrant.io");
        authPolicyCrd.setMetadata(authMeta);

        boolean hasKuadrant = csvSpecs != null && csvSpecs.stream().anyMatch(s -> {
            String n = s.name().toLowerCase();
            return n.contains("kuadrant") || n.contains("rhcl") || n.contains("rh-connectivity");
        });

        when(client.apiextensions()).thenReturn(apiextensions);
        when(apiextensions.v1()).thenReturn(v1);
        when(v1.customResourceDefinitions()).thenReturn(crdOps);
        when(crdOps.withName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            Resource resource = mock(Resource.class);
            if ("gatewayclasses.gateway.networking.k8s.io".equals(name)) {
                when(resource.get()).thenReturn(gatewayCrd);
            } else if ("authpolicies.kuadrant.io".equals(name)) {
                when(resource.get()).thenReturn(hasKuadrant ? authPolicyCrd : null);
            } else {
                when(resource.get()).thenReturn(null);
            }
            return resource;
        });
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

    private static void assertSoftFailOssmNullButExpected(ClusterVersionsResponse response) {
        assertNull(response.ossm, "soft-fail must not present expected OSSM as detected");
        assertFalse(response.capabilities.ossmPresent);
        assertNotNull(response.ossmExpectedForOcp);
        assertEquals("2.6", response.ossmExpectedForOcp);
        assertFalse(response.capabilities.ossmMatchesOcp);
    }

    // ── WU2: resolveFromSettings (I4/I5/S1/I6) ────────────────────────────────

    @Test
    void resolveFromSettings_usesProfileFromReadClusterProfile() {
        ClusterVersionService withSettings = new SettingsProfileService(
                null, ClusterVersionService.PROFILE_OCP_419);

        ClusterVersionsResponse response = withSettings.resolveFromSettings(true);

        assertEquals(ClusterVersionService.PROFILE_OCP_419, response.profile);
        assertEquals("profile", response.source);
        assertTrue(response.ocp.startsWith("4.19"));
        assertTrue(response.capabilities.clusterReachable);
    }

    @Test
    void resolveFromSettings_whenReadFails_fallsBackToAuto() {
        ClusterVersionService failing = new SettingsProfileService(null, null) {
            @Override
            protected String readClusterProfile() {
                throw new RuntimeException("Panache unavailable");
            }
        };

        ClusterVersionsResponse response = failing.resolveFromSettings(true);

        assertEquals(ClusterVersionService.PROFILE_AUTO, response.profile);
        assertEquals("default", response.source);
        assertSoftFailOssmNullButExpected(response);
        assertFalse(response.capabilities.clusterReachable);
    }

    @Test
    void resolveFromSettings_blankSettings_usesAuto() {
        ClusterVersionService blank = new SettingsProfileService(null, "   ");

        ClusterVersionsResponse response = blank.resolveFromSettings(true);

        assertEquals(ClusterVersionService.PROFILE_AUTO, response.profile);
        assertEquals("default", response.source);
    }

    @Test
    void resolveFromSettings_ocp421_wiresCapabilities() {
        ClusterVersionService withSettings = new SettingsProfileService(
                null, ClusterVersionService.PROFILE_OCP_421);

        ClusterVersionsResponse response = withSettings.resolveFromSettings(false);

        assertEquals(ClusterVersionService.PROFILE_OCP_421, response.profile);
        assertEquals("profile", response.source);
        assertTrue(response.ocp.startsWith("4.21"));
        assertTrue(response.capabilities.corsNative);
        assertTrue(response.capabilities.clusterReachable);
    }

    /** Test double with injectable clock for TTL assertions. */
    private static final class MutableClockService extends ClusterVersionService {
        long now;

        MutableClockService(KubernetesClient client, long nowMs) {
            super(client);
            this.now = nowMs;
            useDetectExecutor(Runnable::run);
        }

        @Override
        protected long nowMs() {
            return now;
        }
    }

    /** Test double with overridable settings profile (avoids Panache). */
    private static class SettingsProfileService extends ClusterVersionService {
        private final String profileValue;

        SettingsProfileService(KubernetesClient client, String profileValue) {
            super(client);
            this.profileValue = profileValue;
            useDetectExecutor(Runnable::run);
        }

        @Override
        protected String readClusterProfile() {
            if (profileValue == null || profileValue.isBlank()) {
                return PROFILE_AUTO;
            }
            return profileValue.trim();
        }
    }
}
