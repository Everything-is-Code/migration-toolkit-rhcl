package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves OCP / Gateway API / Kuadrant / OSSM versions and capability matrix.
 * Precedence: manual profile &gt; live detect &gt; soft-fail default (OCP 4.19 / GAPI 1.2.1).
 *
 * <p>OCP↔OSSM expected map (minimum rows for supported profiles):
 * <ul>
 *   <li>OCP 4.19 → OSSM 2.6 — documented on OpenShift 4.19 Service Mesh docs (OSSM 2.6 SMCP path);
 *       OSSM 3.x is also supported on OCP ≥ 4.18 per OSSM 3 release notes.</li>
 *   <li>OCP 4.21 → OSSM 3.0 — OpenShift 4.21 documents Service Mesh 3.x as the current path;
 *       OSSM 3.x support tables cover OCP 4.18–4.22.</li>
 * </ul>
 * Sources: Red Hat OpenShift Service Mesh release notes / OCP Service Mesh docs.
 * Update this table when Red Hat publishes new supported pairs.
 */
@ApplicationScoped
public class ClusterVersionService {

    private static final Logger LOG = Logger.getLogger(ClusterVersionService.class);

    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_OCP_419 = "ocp-4.19";
    public static final String PROFILE_OCP_421 = "ocp-4.21";
    public static final String SETTINGS_KEY_CLUSTER_PROFILE = "clusterProfile";

    public static final String DEFAULT_OCP = "4.19.0";
    public static final String DEFAULT_GATEWAY_API = "1.2.1";

    /**
     * Static OCP major.minor → expected OSSM major.minor.
     * See class javadoc for documentation sources.
     */
    private static final Map<String, String> OCP_TO_OSSM = Map.of(
            "4.19", "2.6",
            "4.21", "3.0"
    );

    private static final Pattern SECRETISH = Pattern.compile(
            "(?i)(token\\s*=\\s*\\S+|bearer\\s+\\S+|sha256~\\S+|kubeconfig\\s*=\\s*\\S+|/[\\w./-]*\\.kube[\\w./-]*)");

    @Inject
    KubernetesClient client;

    private volatile ClusterVersionsResponse cache;

    public ClusterVersionService() {
    }

    /** Unit-test constructor. */
    public ClusterVersionService(KubernetesClient client) {
        this.client = client;
    }

    public ClusterVersionsResponse resolve(String profile, boolean refresh) {
        if (!refresh && cache != null && sameProfile(cache.profile, profile)) {
            return cache;
        }
        ClusterVersionsResponse resolved = doResolve(normalizeProfile(profile));
        cache = resolved;
        return resolved;
    }

    private static boolean sameProfile(String cached, String requested) {
        return normalizeProfile(cached).equals(normalizeProfile(requested));
    }

    private static String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return PROFILE_AUTO;
        }
        return profile.trim();
    }

    private ClusterVersionsResponse doResolve(String profile) {
        if (PROFILE_OCP_419.equals(profile) || PROFILE_OCP_421.equals(profile)) {
            return applyProfile(profile);
        }
        return detectOrDefault(profile);
    }

    private ClusterVersionsResponse applyProfile(String profile) {
        ClusterVersionsResponse r = new ClusterVersionsResponse();
        r.source = "profile";
        r.profile = profile;
        if (PROFILE_OCP_421.equals(profile)) {
            r.ocp = "4.21.0";
            r.gatewayApi = "1.3.0";
            r.kuadrant = null;
            r.ossmExpectedForOcp = expectedOssmForOcp(r.ocp);
            r.ossm = r.ossmExpectedForOcp;
            r.capabilities = capabilitiesFrom(r.ocp, r.gatewayApi, "1.0.0", r.ossm, r.ossmExpectedForOcp);
            // Profile 4.21 assumes Kuadrant available for matrix purposes when not detecting
            r.capabilities.kuadrantPresent = true;
            r.capabilities.ossmPresent = true;
            r.capabilities.ossmMatchesOcp = true;
        } else {
            r.ocp = DEFAULT_OCP;
            r.gatewayApi = DEFAULT_GATEWAY_API;
            r.kuadrant = null;
            r.ossmExpectedForOcp = expectedOssmForOcp(r.ocp);
            r.ossm = r.ossmExpectedForOcp;
            r.capabilities = capabilitiesFrom(r.ocp, r.gatewayApi, null, r.ossm, r.ossmExpectedForOcp);
            r.capabilities.ossmPresent = true;
            r.capabilities.ossmMatchesOcp = true;
        }
        return r;
    }

    private ClusterVersionsResponse detectOrDefault(String profile) {
        List<String> errors = new ArrayList<>();
        if (client == null) {
            return softFailDefault(profile, errors, "No Kubernetes client available");
        }

        String ocp = null;
        String gatewayApi = null;
        String kuadrant = null;
        String ossm = null;
        boolean hardFail = false;

        try {
            ocp = detectOcp();
        } catch (Exception e) {
            LOG.warnf("OCP version detect failed: %s", e.getMessage());
            errors.add(sanitize("OCP detect failed: " + safeMessage(e)));
            if (isAccessOrMissing(e)) {
                hardFail = true;
            }
        }

        try {
            gatewayApi = detectGatewayApiBundleVersion();
        } catch (Exception e) {
            LOG.warnf("Gateway API detect failed: %s", e.getMessage());
            errors.add(sanitize("Gateway API detect failed: " + safeMessage(e)));
        }

        try {
            kuadrant = detectKuadrantCsvVersion();
        } catch (Exception e) {
            LOG.warnf("Kuadrant CSV detect failed: %s", e.getMessage());
            errors.add(sanitize("Kuadrant detect failed: " + safeMessage(e)));
        }

        try {
            ossm = detectOssmCsvVersion();
        } catch (Exception e) {
            LOG.warnf("OSSM CSV detect failed: %s", e.getMessage());
            errors.add(sanitize("OSSM CSV detect failed: " + safeMessage(e)));
        }

        try {
            String smcp = detectSmcpVersion();
            if (ossm == null && smcp != null) {
                ossm = smcp;
            }
        } catch (Exception e) {
            LOG.warnf("SMCP detect failed (CSV/map fallback ok): %s", e.getMessage());
            errors.add(sanitize("SMCP detect failed: " + safeMessage(e)));
        }

        if (hardFail || ocp == null) {
            return softFailDefault(profile, errors, null);
        }

        String expected = expectedOssmForOcp(ocp);
        if (ossm == null && expected != null) {
            ossm = expected;
            errors.add("OSSM not detected; using OCP→OSSM expected map value " + expected);
        }
        if (gatewayApi == null) {
            gatewayApi = DEFAULT_GATEWAY_API;
            errors.add("Gateway API bundle-version not detected; using " + DEFAULT_GATEWAY_API);
        }

        ClusterVersionsResponse r = new ClusterVersionsResponse();
        r.source = "detected";
        r.profile = PROFILE_AUTO.equals(profile) ? PROFILE_AUTO : profile;
        r.ocp = ocp;
        r.gatewayApi = stripLeadingV(gatewayApi);
        r.kuadrant = kuadrant;
        r.ossm = ossm;
        r.ossmExpectedForOcp = expected;
        r.capabilities = capabilitiesFrom(r.ocp, r.gatewayApi, r.kuadrant, r.ossm, r.ossmExpectedForOcp);
        r.errors = errors;
        return r;
    }

    private ClusterVersionsResponse softFailDefault(String profile, List<String> errors, String extra) {
        if (extra != null) {
            errors.add(sanitize(extra));
        }
        ClusterVersionsResponse r = new ClusterVersionsResponse();
        r.source = "default";
        r.profile = PROFILE_AUTO.equals(profile) ? PROFILE_AUTO : profile;
        r.ocp = DEFAULT_OCP;
        r.gatewayApi = DEFAULT_GATEWAY_API;
        r.kuadrant = null;
        r.ossmExpectedForOcp = expectedOssmForOcp(r.ocp);
        r.ossm = r.ossmExpectedForOcp;
        r.capabilities = capabilitiesFrom(r.ocp, r.gatewayApi, null, null, r.ossmExpectedForOcp);
        r.errors = errors;
        return r;
    }

    @SuppressWarnings("unchecked")
    private String detectOcp() {
        ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                .withGroup("config.openshift.io")
                .withVersion("v1")
                .withKind("ClusterVersion")
                .withPlural("clusterversions")
                .withNamespaced(false)
                .build();
        GenericKubernetesResource cv = client.genericKubernetesResources(ctx).withName("version").get();
        if (cv == null) {
            throw new KubernetesClientException("ClusterVersion/version not found");
        }
        Object statusObj = cv.getAdditionalProperties().get("status");
        if (!(statusObj instanceof Map<?, ?> status)) {
            throw new IllegalStateException("ClusterVersion has no status");
        }
        Object desired = status.get("desired");
        if (desired instanceof Map<?, ?> desiredMap) {
            Object version = desiredMap.get("version");
            if (version != null && !version.toString().isBlank()) {
                return version.toString();
            }
        }
        Object history = status.get("history");
        if (history instanceof List<?> hist && !hist.isEmpty() && hist.get(0) instanceof Map<?, ?> first) {
            Object version = first.get("version");
            if (version != null) {
                return version.toString();
            }
        }
        throw new IllegalStateException("ClusterVersion status has no version");
    }

    private String detectGatewayApiBundleVersion() {
        CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions()
                .withName("gatewayclasses.gateway.networking.k8s.io")
                .get();
        if (crd == null || crd.getMetadata() == null || crd.getMetadata().getAnnotations() == null) {
            return null;
        }
        String bundle = crd.getMetadata().getAnnotations().get("gateway.networking.k8s.io/bundle-version");
        return bundle == null ? null : stripLeadingV(bundle);
    }

    private String detectKuadrantCsvVersion() {
        return findCsvVersion(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.contains("kuadrant") || lower.contains("rhcl") || lower.contains("rh-connectivity");
        });
    }

    private String detectOssmCsvVersion() {
        return findCsvVersion(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            // Prefer explicit OSSM / service mesh operator names — never treat generic "istio" alone as OSSM
            return lower.contains("servicemesh")
                    || lower.contains("openshift-service-mesh")
                    || lower.contains("ossm")
                    || (lower.contains("sail") && lower.contains("operator"));
        });
    }

    private String findCsvVersion(java.util.function.Predicate<String> nameMatch) {
        ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1alpha1")
                .withKind("ClusterServiceVersion")
                .withPlural("clusterserviceversions")
                .withNamespaced(true)
                .build();
        GenericKubernetesResourceList list = client.genericKubernetesResources(ctx).inAnyNamespace().list();
        if (list == null || list.getItems() == null) {
            return null;
        }
        Optional<GenericKubernetesResource> match = list.getItems().stream()
                .filter(csv -> csv.getMetadata() != null && csv.getMetadata().getName() != null)
                .filter(csv -> nameMatch.test(csv.getMetadata().getName()))
                .findFirst();
        return match.map(ClusterVersionService::csvVersion).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static String csvVersion(GenericKubernetesResource csv) {
        Object spec = csv.getAdditionalProperties().get("spec");
        if (spec instanceof Map<?, ?> specMap) {
            Object version = specMap.get("version");
            if (version != null && !version.toString().isBlank()) {
                return version.toString();
            }
        }
        String name = csv.getMetadata().getName();
        int idx = name.lastIndexOf(".v");
        if (idx >= 0 && idx + 2 < name.length()) {
            return name.substring(idx + 2);
        }
        return name;
    }

    /**
     * Optional SMCP read. Tries {@code maistra.io} then {@code sailoperator.io}.
     * Missing RBAC soft-fails to caller (CSV / OCP map).
     */
    @SuppressWarnings("unchecked")
    private String detectSmcpVersion() {
        for (String group : List.of("maistra.io", "sailoperator.io")) {
            try {
                ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                        .withGroup(group)
                        .withVersion("v1")
                        .withKind("ServiceMeshControlPlane")
                        .withPlural("servicemeshcontrolplanes")
                        .withNamespaced(true)
                        .build();
                GenericKubernetesResourceList list = client.genericKubernetesResources(ctx).inAnyNamespace().list();
                if (list == null || list.getItems() == null || list.getItems().isEmpty()) {
                    continue;
                }
                GenericKubernetesResource smcp = list.getItems().get(0);
                Object spec = smcp.getAdditionalProperties().get("spec");
                if (spec instanceof Map<?, ?> specMap) {
                    Object version = specMap.get("version");
                    if (version != null && !version.toString().isBlank()) {
                        return version.toString();
                    }
                }
                Object status = smcp.getAdditionalProperties().get("status");
                if (status instanceof Map<?, ?> statusMap) {
                    Object version = statusMap.get("version");
                    if (version != null) {
                        return version.toString();
                    }
                }
            } catch (KubernetesClientException e) {
                if (e.getCode() == 403 || e.getCode() == 404) {
                    throw e;
                }
                LOG.debugf("SMCP group %s not usable: %s", group, e.getMessage());
            }
        }
        return null;
    }

    public static String expectedOssmForOcp(String ocpVersion) {
        if (ocpVersion == null || ocpVersion.isBlank()) {
            return null;
        }
        String majorMinor = majorMinor(ocpVersion);
        return OCP_TO_OSSM.get(majorMinor);
    }

    public static ClusterCapabilities capabilitiesFrom(
            String ocp,
            String gatewayApi,
            String kuadrant,
            String ossm,
            String ossmExpected) {
        ClusterCapabilities caps = new ClusterCapabilities();
        boolean gapiNative = gatewayApi != null && compareVersions(stripLeadingV(gatewayApi), "1.3") >= 0;
        boolean ocpNative = ocp != null && compareVersions(majorMinor(ocp), "4.21") >= 0;
        caps.corsNative = gapiNative || ocpNative;
        caps.kuadrantPresent = kuadrant != null && !kuadrant.isBlank();
        caps.ossmPresent = ossm != null && !ossm.isBlank();
        caps.ossmMatchesOcp = caps.ossmPresent && ossmExpected != null
                && compareVersions(majorMinor(ossm), majorMinor(ossmExpected)) == 0;
        boolean gapiTimeouts = gatewayApi != null && compareVersions(stripLeadingV(gatewayApi), "1.1") >= 0;
        boolean ocpTimeouts = ocp != null && compareVersions(majorMinor(ocp), "4.18") >= 0;
        caps.timeoutsSupported = gapiTimeouts || ocpTimeouts
                || (ocp != null && compareVersions(majorMinor(ocp), "4.19") >= 0);
        return caps;
    }

    public static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        if (v == null || v.isBlank()) {
            return new int[]{0};
        }
        String cleaned = stripLeadingV(v).split("[+-]")[0];
        String[] parts = cleaned.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String majorMinor(String version) {
        int[] p = parseVersion(version);
        if (p.length == 0) {
            return "0.0";
        }
        if (p.length == 1) {
            return p[0] + ".0";
        }
        return p[0] + "." + p[1];
    }

    private static String stripLeadingV(String version) {
        if (version == null) {
            return null;
        }
        String t = version.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            return t.substring(1);
        }
        return t;
    }

    private static boolean isAccessOrMissing(Throwable e) {
        if (e instanceof KubernetesClientException kce) {
            int code = kce.getCode();
            return code == 401 || code == 403 || code == 404 || code == -1;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return true;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("forbidden") || lower.contains("unauthorized")
                || lower.contains("not found") || lower.contains("connection");
    }

    private static String safeMessage(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    static String sanitize(String message) {
        if (message == null) {
            return "detection error";
        }
        String cleaned = SECRETISH.matcher(message).replaceAll("[redacted]");
        // Drop absolute home paths that might remain
        cleaned = cleaned.replaceAll("(?i)/home/\\S+", "[redacted-path]");
        cleaned = cleaned.replaceAll("(?i)/Users/\\S+", "[redacted-path]");
        return cleaned;
    }

    /** Exposed for diagnostics / future Settings wiring. */
    public Map<String, String> ossmCompatibilityTable() {
        return new LinkedHashMap<>(OCP_TO_OSSM);
    }
}
