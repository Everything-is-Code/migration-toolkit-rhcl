package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient;
import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.dto.ServiceListPage;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Metric;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ThreeScaleExportService {

    private static final Logger LOG = Logger.getLogger(ThreeScaleExportService.class);

    /** Page size for Admin API list endpoints (shared with {@link ThreeScaleClient} @DefaultValue). */
    static final int LIST_PAGE_SIZE = ConversionConstants.LIST_PAGE_SIZE;

    /** Max wait for list-enrich virtual-thread pool shutdown. */
    static final long LIST_ENRICH_TERMINATION_SECONDS = 60;

    /** Short TTL for in-memory export/catalog/apps caches keyed by (url, token fingerprint, …). */
    static final long EXPORT_CACHE_TTL_MS = 60_000L;

    /** Default / max page sizes for the selection UI (maps to 3scale Admin API page/per_page). */
    public static final int DEFAULT_UI_PAGE_SIZE = 20;
    public static final int MAX_UI_PAGE_SIZE = 100;

    private final ConcurrentHashMap<String, CachedExport> exportCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedCatalog> backendCatalogCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedApplications> applicationsCache = new ConcurrentHashMap<>();

    private record CachedExport(ApiService service, long expiresAtMs) {
    }

    private record CachedCatalog(Map<String, Backend> catalog, long expiresAtMs) {
    }

    private record CachedApplications(List<Map<String, Object>> apps, long expiresAtMs) {
    }

    @ConfigProperty(name = "threescale.connect-timeout")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "threescale.detect-timeout")
    int detectTimeoutSeconds;

    /**
     * Runtime-overridable page size for Admin API list requests. Defaults to
     * {@link #LIST_PAGE_SIZE} so plain construction (e.g. unit tests without CDI)
     * keeps the same behavior. Override via {@code THREESCALE_PAGE_SIZE} when CDI-managed.
     */
    @ConfigProperty(name = "threescale.page-size", defaultValue = ConversionConstants.LIST_PAGE_SIZE_DEFAULT)
    int pageSize = LIST_PAGE_SIZE;

    public boolean testConnection(ConnectionRequest req) {
        try {
            ThreeScaleClient client = buildClient(req.url);
            Map<String, Object> result = client.getServices(req.accessToken, 1, 1);
            return result != null;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to test 3scale connection to %s: %s", req.url, e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to detect the 3scale version from response headers of /admin/api/account.json.
     * Returns null if version cannot be determined.
     */
    public String detectVersion(String url, String accessToken) {
        try {
            String accountUrl = url.replaceAll("/+$", "") + "/admin/api/account.json?access_token=" + accessToken;
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(accountUrl))
                    .timeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            Pattern versionPattern = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

            // 1. Check known version response headers
            for (String header : List.of("x-3scale-version", "x-powered-by", "server", "via")) {
                Optional<String> value = response.headers().firstValue(header);
                if (value.isPresent()) {
                    Matcher m = versionPattern.matcher(value.get());
                    if (m.find()) {
                        String candidate = m.group(1);
                        String[] parts = candidate.split("\\.");
                        if (parts.length >= 2 && Integer.parseInt(parts[0]) >= 2) {
                            LOG.infof("Detected 3scale version from header '%s': %s", header, candidate);
                            return candidate;
                        }
                    }
                }
            }

            // 2. Parse HTML body from the admin dashboard for embedded version strings
            String dashboardUrl = url.replaceAll("/+$", "") + "/p/admin/dashboard";
            HttpRequest dashRequest = HttpRequest.newBuilder()
                    .uri(new URI(dashboardUrl + "?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(detectTimeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> dashResponse = httpClient.send(dashRequest, HttpResponse.BodyHandlers.ofString());
            String body = dashResponse.body();

            // Patterns commonly found in 3scale HTML pages
            List<Pattern> bodyPatterns = List.of(
                // <meta name="3scale-version" content="2.16.0">
                Pattern.compile(
                    "(?:name=[\"']3scale-version[\"']\\s+content=[\"']|content=[\"']\\s*(?:3scale\\s+)?)"
                    + "([\\d]+\\.[\\d]+(?:\\.[\\d]+)?)[\"']"),
                // <!-- 3scale 2.16.0 --> or <!-- version: 2.16.0 -->
                Pattern.compile("<!--[^>]*(?:3scale|version)[^>]*?([2-9]\\.\\d+(?:\\.\\d+)?)[^>]*-->"),
                // data-version="2.16.0" or data-3scale-version="2.16.0"
                Pattern.compile("data-(?:3scale-)?version=[\"']([2-9]\\.\\d+(?:\\.\\d+)?)[\"']"),
                // ThreeScale.version = "2.16" or window.version = "2.16"
                Pattern.compile(
                    "(?:ThreeScale\\.version|threescale_version|THREESCALE_VERSION)"
                    + "\\s*[=:]\\s*[\"']([2-9]\\.\\d+(?:\\.\\d+)?)[\"']"),
                // Generic: any "2.NN" or "2.NN.N" that appears near "3scale" within 100 chars
                Pattern.compile("3[Ss]cale.{0,100}?([2-9]\\.\\d{1,2}(?:\\.\\d+)?)")
            );

            for (Pattern p : bodyPatterns) {
                Matcher m = p.matcher(body);
                if (m.find()) {
                    String candidate = m.group(1);
                    String[] parts = candidate.split("\\.");
                    if (parts.length >= 2 && Integer.parseInt(parts[0]) >= 2) {
                        LOG.infof("Detected 3scale version from HTML body: %s", candidate);
                        return candidate;
                    }
                }
            }

            LOG.info("Could not detect 3scale version from headers or HTML body");
        } catch (Exception e) {
            LOG.warnf(e, "Failed to detect 3scale version: %s", e.getMessage());
        }
        return null;
    }

    /**
     * Selection UI list: one Admin API page of services, enriched with policies + backends only.
     * Cost scales with {@code perPage}, not with tenant size — suitable for 100+ products.
     */
    public ServiceListPage listServicesPage(String url, String accessToken, int page, int perPage) {
        ThreeScaleClient client = buildClient(url);
        return listServicesPage(client, url, accessToken, page, perPage);
    }

    /**
     * @deprecated Prefer {@link #listServicesPage(String, String, int, int)}.
     * Loads only the first UI-sized page (not the full tenant).
     */
    @Deprecated
    public List<ApiService> listServices(String url, String accessToken) {
        return listServicesPage(url, accessToken, 1, DEFAULT_UI_PAGE_SIZE).items;
    }

    /**
     * @deprecated Prefer {@link #listServicesPage(String, String, int, int)}.
     */
    @Deprecated
    public List<ApiService> exportServices(String url, String accessToken) {
        return listServices(url, accessToken);
    }

    /**
     * Package-visible for unit tests with a mocked {@link ThreeScaleClient}.
     */
    @SuppressWarnings("unchecked")
    ServiceListPage listServicesPage(ThreeScaleClient client, String catalogCacheKey,
                                     String accessToken, int page, int perPage) {
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(MAX_UI_PAGE_SIZE, Math.max(1, perPage));
        try {
            Map<String, Object> response = client.getServices(accessToken, safePage, safePerPage);
            List<Map<String, Object>> pageItems = extractList(response, "services");
            Map<String, Backend> backendCatalog = cachedBackendCatalog(client, catalogCacheKey, accessToken);

            List<ApiService> services = new ArrayList<>();
            for (Map<String, Object> svcWrapper : pageItems) {
                Map<String, Object> svc = (Map<String, Object>) svcWrapper.get("service");
                if (svc == null) {
                    continue;
                }
                services.add(mapServiceSummary(svc));
            }

            enrichListSummaries(client, accessToken, services, backendCatalog);

            ServiceListPage out = new ServiceListPage();
            out.items = services;
            out.page = safePage;
            out.perPage = safePerPage;
            out.hasMore = pageItems.size() >= safePerPage;
            if (!out.hasMore) {
                out.total = (safePage - 1) * safePerPage + services.size();
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list services from 3scale: " + e.getMessage(), e);
        }
    }

    /** Package-visible overload used by older unit tests (no catalog cache key). */
    List<ApiService> listServices(ThreeScaleClient client, String accessToken) {
        return listServicesPage(client, "test", accessToken, 1, DEFAULT_UI_PAGE_SIZE).items;
    }

    private Map<String, Backend> cachedBackendCatalog(ThreeScaleClient client, String url,
                                                      String accessToken) {
        String key = credentialCacheKey(url, accessToken);
        long now = System.currentTimeMillis();
        CachedCatalog hit = backendCatalogCache.get(key);
        if (hit != null && hit.expiresAtMs() > now) {
            return hit.catalog();
        }
        Map<String, Backend> catalog = fetchBackendCatalog(client, accessToken);
        backendCatalogCache.put(key, new CachedCatalog(catalog, now + EXPORT_CACHE_TTL_MS));
        return catalog;
    }

    /** Clears export + backend-catalog + applications caches (tests / refresh). */
    public void clearExportCache() {
        exportCache.clear();
        backendCatalogCache.clear();
        applicationsCache.clear();
    }

    private ApiService mapServiceSummary(Map<String, Object> svc) {
        ApiService service = new ApiService();
        service.id = String.valueOf(svc.get("id"));
        service.name = (String) svc.get("name");
        service.description = (String) svc.get("description");
        service.state = (String) svc.get("state");
        service.systemName = (String) svc.get("system_name");
        service.backendVersion = (String) svc.get("backend_version");
        service.deploymentOption = (String) svc.get("deployment_option");
        service.authentication = extractAuthentication(svc);
        return service;
    }

    private void enrichListSummaries(ThreeScaleClient client, String accessToken,
                                     List<ApiService> services, Map<String, Backend> backendCatalog) {
        if (services.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(services.size());
            for (ApiService service : services) {
                futures.add(CompletableFuture.runAsync(() -> {
                    service.policies = fetchPolicies(client, service.id, accessToken);
                    service.backends = resolveBackendsFromUsages(
                            client, service.id, accessToken, backendCatalog);
                }, pool));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).join();
                } catch (CompletionException e) {
                    ApiService failed = services.get(i);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.warnf(cause, "Failed to enrich service %s during list: %s",
                            failed != null ? failed.id : "?", cause.getMessage());
                }
            }
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(LIST_ENRICH_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Backend> fetchBackendCatalog(ThreeScaleClient client, String accessToken) {
        Map<String, Backend> catalog = new LinkedHashMap<>();
        try {
            int page = 1;
            while (true) {
                Map<String, Object> response = client.getBackends(accessToken, page, pageSize);
                List<Map<String, Object>> pageItems = extractList(response, "backend_apis");
                if (pageItems.isEmpty()) {
                    pageItems = extractList(response, "backends");
                }
                for (Map<String, Object> wrapper : pageItems) {
                    Map<String, Object> b = wrapper;
                    if (wrapper.get("backend_api") instanceof Map<?, ?> nested) {
                        b = (Map<String, Object>) nested;
                    } else if (wrapper.get("backend") instanceof Map<?, ?> nested) {
                        b = (Map<String, Object>) nested;
                    }
                    Backend backend = new Backend();
                    backend.id = String.valueOf(b.get("id"));
                    backend.name = (String) b.get("name");
                    backend.systemName = (String) b.get("system_name");
                    backend.privateEndpoint = (String) b.get("private_endpoint");
                    if (backend.id != null && !"null".equals(backend.id)) {
                        catalog.put(backend.id, backend);
                    }
                }
                if (pageItems.size() < pageSize) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch backend catalog: %s — continuing with empty catalog (per-backend GET fallback)",
                    e.getMessage());
        }
        if (catalog.isEmpty()) {
            LOG.warn("Backend catalog is empty; list enrichment will fall back to per-backend GET when needed");
        }
        return catalog;
    }

    @SuppressWarnings("unchecked")
    List<Backend> resolveBackendsFromUsages(ThreeScaleClient client, String serviceId,
                                            String accessToken, Map<String, Backend> catalog) {
        try {
            List<Map<String, Object>> usages = client.getBackendUsages(serviceId, accessToken);
            List<Backend> backends = new ArrayList<>();
            for (Map<String, Object> uw : usages) {
                Map<String, Object> usage = (Map<String, Object>) uw.get("backend_usage");
                if (usage == null) {
                    continue;
                }
                Object backendIdObj = usage.get("backend_id");
                if (backendIdObj == null) {
                    continue;
                }
                String backendId = String.valueOf(backendIdObj);
                Backend fromCatalog = catalog.get(backendId);
                if (fromCatalog != null) {
                    backends.add(cloneBackendWithUsage(fromCatalog, usage));
                    continue;
                }
                // Fallback only when catalog miss (should be rare)
                try {
                    Map<String, Object> bResp = client.getBackend(backendId, accessToken);
                    Map<String, Object> b = (Map<String, Object>) bResp.get("backend_api");
                    if (b == null) {
                        continue;
                    }
                    Backend backend = new Backend();
                    backend.id = String.valueOf(b.get("id"));
                    backend.name = (String) b.get("name");
                    backend.systemName = (String) b.get("system_name");
                    backend.privateEndpoint = (String) b.get("private_endpoint");
                    backends.add(cloneBackendWithUsage(backend, usage));
                } catch (Exception ex) {
                    LOG.warnf(ex, "Failed to fetch backend %s: %s", backendId, ex.getMessage());
                }
            }
            return backends;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch backend usages for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public ApiService exportService(String url, String accessToken, String serviceId) {
        return exportService(buildClient(url), url, accessToken, serviceId);
    }

    /**
     * Package-visible for unit tests with a mocked {@link ThreeScaleClient}.
     * Caches by normalized {@code (url, tokenFingerprint, serviceId)} for {@link #EXPORT_CACHE_TTL_MS}.
     */
    ApiService exportService(ThreeScaleClient client, String url, String accessToken, String serviceId) {
        String key = exportCacheKey(url, accessToken, serviceId);
        long now = exportNowMs();
        CachedExport hit = exportCache.get(key);
        if (hit != null && hit.expiresAtMs > now) {
            return hit.service;
        }
        ApiService fetched = fetchExport(client, url, accessToken, serviceId);
        exportCache.put(key, new CachedExport(fetched, now + EXPORT_CACHE_TTL_MS));
        return fetched;
    }

    /**
     * Cache key for per-service export payloads. Includes a SHA-256 fingerprint of the access
     * token so concurrent sessions against the same URL cannot share cached data.
     */
    static String exportCacheKey(String url, String accessToken, String serviceId) {
        String id = serviceId == null ? "" : serviceId.trim();
        return credentialCacheKey(url, accessToken) + "|" + id;
    }

    /** Cache key shared by backend catalog and tenant-wide applications list. */
    static String credentialCacheKey(String url, String accessToken) {
        String normalizedUrl = url == null ? "" : url.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
        return normalizedUrl + "|" + tokenFingerprint(accessToken);
    }

    /** SHA-256 hex of the token (never store the raw token in cache keys / logs). */
    static String tokenFingerprint(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // Extremely unlikely on a standard JVM; fall back to length-only sentinel.
            return "len:" + accessToken.length();
        }
    }

    /** Package-visible clock hook for TTL tests. */
    long exportNowMs() {
        return System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private ApiService fetchExport(ThreeScaleClient client, String url, String accessToken, String serviceId) {
        Map<String, Object> response = client.getService(serviceId, accessToken);
        Map<String, Object> svc = (Map<String, Object>) response.get("service");

        ApiService service = new ApiService();
        service.id = String.valueOf(svc.get("id"));
        service.name = (String) svc.get("name");
        service.description = (String) svc.get("description");
        service.systemName = (String) svc.get("system_name");
        service.backendVersion = (String) svc.get("backend_version");
        service.deploymentOption = (String) svc.get("deployment_option");
        service.authentication = extractAuthentication(svc);

        // Parallelize independent Admin API legs (F2). Backends reuse the shared catalog (F3).
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            CompletableFuture<List<Policy>> policiesF = CompletableFuture.supplyAsync(
                    () -> fetchPolicies(client, serviceId, accessToken), pool);
            CompletableFuture<List<MappingRule>> rulesF = CompletableFuture.supplyAsync(
                    () -> fetchMappingRules(client, serviceId, accessToken), pool);
            CompletableFuture<List<Metric>> metricsF = CompletableFuture.supplyAsync(
                    () -> fetchMetrics(client, serviceId, accessToken), pool);
            CompletableFuture<List<Backend>> backendsF = CompletableFuture.supplyAsync(() -> {
                Map<String, Backend> catalog = cachedBackendCatalog(client, url, accessToken);
                return resolveBackendsFromUsages(client, serviceId, accessToken, catalog);
            }, pool);
            CompletableFuture<List<Application>> appsF = CompletableFuture.supplyAsync(
                    () -> fetchApplications(client, url, serviceId, accessToken), pool);
            CompletableFuture<List<ApplicationPlan>> plansF = CompletableFuture.supplyAsync(
                    () -> fetchApplicationPlans(client, serviceId, accessToken), pool);

            service.policies = policiesF.join();
            service.mappingRules = rulesF.join();
            service.metrics = metricsF.join();
            service.backends = backendsF.join();
            service.applications = appsF.join();
            service.applicationPlans = plansF.join();
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(LIST_ENRICH_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return service;
    }

    private List<Policy> fetchPolicies(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            Map<String, Object> resp = client.getPolicies(serviceId, accessToken);
            List<Map<String, Object>> policyList = extractList(resp, "policies_config");
            List<Policy> policies = new ArrayList<>();
            for (Map<String, Object> p : policyList) {
                Policy policy = new Policy();
                policy.name = (String) p.get("name");
                policy.version = (String) p.get("version");
                policy.enabled = (Boolean) p.getOrDefault("enabled", true);
                policy.configuration = (Map<String, Object>) p.get("configuration");
                policies.add(policy);
            }
            return policies;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch policies for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    List<MappingRule> fetchMappingRules(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            Map<String, Object> resp = client.getMappingRules(serviceId, accessToken);
            List<Map<String, Object>> ruleList = extractList(resp, "mapping_rules");
            List<MappingRule> rules = new ArrayList<>();
            for (Map<String, Object> rw : ruleList) {
                Map<String, Object> r = (Map<String, Object>) rw.get("mapping_rule");
                if (r == null) {
                    r = rw;
                }
                MappingRule rule = new MappingRule();
                rule.id = String.valueOf(r.get("id"));
                rule.httpMethod = (String) r.get("http_method");
                rule.pattern = (String) r.get("pattern");
                rule.metricSystemName = (String) r.get("metric_system_name");
                rule.last = (Boolean) r.getOrDefault("last", false);
                rules.add(rule);
            }
            return rules;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch mapping rules for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    List<Metric> fetchMetrics(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            Map<String, Object> resp = client.getMetrics(serviceId, accessToken);
            List<Map<String, Object>> metricList = extractList(resp, "metrics");
            List<Metric> metrics = new ArrayList<>();
            for (Map<String, Object> mw : metricList) {
                Map<String, Object> m = (Map<String, Object>) mw.get("metric");
                if (m == null) {
                    m = mw;
                }
                Metric metric = new Metric();
                metric.id = String.valueOf(m.get("id"));
                metric.name = (String) m.get("friendly_name");
                metric.systemName = (String) m.get("system_name");
                metric.unit = (String) m.get("unit");
                metrics.add(metric);
            }
            return metrics;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch metrics for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Clone a catalog/API Backend and attach usage-specific mount path and optional weight
     * without mutating the shared catalog entry.
     */
    static Backend cloneBackendWithUsage(Backend source, Map<String, Object> usage) {
        Backend clone = new Backend();
        if (source != null) {
            clone.id = source.id;
            clone.name = source.name;
            clone.description = source.description;
            clone.systemName = source.systemName;
            clone.privateEndpoint = source.privateEndpoint;
            clone.mappingRules = source.mappingRules;
            clone.metrics = source.metrics;
        }
        Object pathObj = usage != null ? usage.get("path") : null;
        clone.path = normalizeMountPath(pathObj != null ? String.valueOf(pathObj) : null);
        if (usage != null) {
            clone.weight = parseOptionalWeight(usage.get("weight"));
        }
        return clone;
    }

    static String normalizeMountPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.trim();
    }

    private static Integer parseOptionalWeight(Object weightObj) {
        if (weightObj == null) {
            return null;
        }
        if (weightObj instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(weightObj).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Authentication extractAuthentication(Map<String, Object> svc) {
        Authentication auth = new Authentication();
        String backendVersion = (String) svc.get("backend_version");
        if ("1".equals(backendVersion)) {
            auth.type = "apiKey";
        } else if ("2".equals(backendVersion)) {
            auth.type = "appIdKey";
        } else if ("oidc".equals(backendVersion)) {
            auth.type = "jwt";
        } else {
            auth.type = "none";
        }
        return auth;
    }

    /**
     * Fetch applications for a service and their application keys from the Admin API.
     * Uses tenant-wide {@code /admin/api/applications.json} (cached briefly per url+token)
     * and filters by {@code service_id}. Real credentials only — never invent keys.
     */
    @SuppressWarnings("unchecked")
    List<Application> fetchApplications(ThreeScaleClient client, String url,
                                        String serviceId, String accessToken) {
        try {
            List<Map<String, Object>> tenantApps = cachedTenantApplications(client, url, accessToken);
            List<Application> applications = new ArrayList<>();
            int matched = 0;
            for (Map<String, Object> wrapper : tenantApps) {
                Map<String, Object> appMap = wrapper;
                if (wrapper.get("application") instanceof Map<?, ?> nested) {
                    appMap = (Map<String, Object>) nested;
                }
                if (!serviceIdMatches(appMap.get("service_id"), serviceId)) {
                    continue;
                }
                matched++;
                Application app = new Application();
                app.id = String.valueOf(appMap.get("id"));
                app.name = (String) appMap.get("name");
                Object applicationId = appMap.get("application_id");
                if (applicationId == null) {
                    applicationId = appMap.get("user_key");
                }
                app.appId = applicationId != null ? String.valueOf(applicationId) : null;
                app.keys = fetchApplicationKeys(client, app.id, accessToken);
                applications.add(app);
            }
            if (!tenantApps.isEmpty() && matched == 0) {
                LOG.debugf("No applications matched service_id=%s among %d tenant apps",
                        serviceId, tenantApps.size());
            }
            return applications;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch applications for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tenant-wide applications list with short TTL, keyed by url + token fingerprint.
     * Shared across {@link #fetchApplications} calls in a bulk export/convert.
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cachedTenantApplications(ThreeScaleClient client, String url,
                                                       String accessToken) {
        String key = credentialCacheKey(url, accessToken);
        long now = System.currentTimeMillis();
        CachedApplications hit = applicationsCache.get(key);
        if (hit != null && hit.expiresAtMs() > now) {
            return hit.apps();
        }
        List<Map<String, Object>> all = fetchAllApplicationPages(client, accessToken);
        applicationsCache.put(key, new CachedApplications(all, now + EXPORT_CACHE_TTL_MS));
        return all;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fetchAllApplicationPages(ThreeScaleClient client, String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        while (true) {
            Map<String, Object> resp = client.getApplications(accessToken, page, pageSize);
            List<Map<String, Object>> appList = extractList(resp, "applications");
            if (appList.isEmpty() && page == 1 && resp.containsKey("application")) {
                appList = List.of(resp);
            }
            all.addAll(appList);
            if (appList.size() < pageSize) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * Compare Admin API {@code service_id} (number or string) to the requested service id.
     * Numeric values match even when string forms differ (e.g. {@code 7} vs {@code "07"}).
     */
    static boolean serviceIdMatches(Object serviceIdField, String expectedServiceId) {
        if (expectedServiceId == null || expectedServiceId.isBlank() || serviceIdField == null) {
            return false;
        }
        String actual = String.valueOf(serviceIdField).trim();
        String expected = expectedServiceId.trim();
        if (expected.equals(actual)) {
            return true;
        }
        try {
            return new java.math.BigDecimal(actual).compareTo(new java.math.BigDecimal(expected)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    List<String> fetchApplicationKeys(ThreeScaleClient client, String applicationId, String accessToken) {
        try {
            Map<String, Object> resp = client.getApplicationKeys(applicationId, accessToken);
            List<Map<String, Object>> keyList = extractList(resp, "keys");
            List<String> keys = new ArrayList<>();
            for (Map<String, Object> wrapper : keyList) {
                Map<String, Object> keyMap = wrapper;
                if (wrapper.get("key") instanceof Map<?, ?> nested) {
                    keyMap = (Map<String, Object>) nested;
                }
                Object value = keyMap.get("value");
                if (value == null) {
                    value = keyMap.get("key");
                }
                if (value != null && !String.valueOf(value).isBlank()) {
                    keys.add(String.valueOf(value));
                }
            }
            return keys;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch keys for application %s: %s", applicationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch application plans and their usage limits from the Admin API.
     */
    @SuppressWarnings("unchecked")
    List<ApplicationPlan> fetchApplicationPlans(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            Map<String, Object> resp = client.getApplicationPlans(serviceId, accessToken);
            List<Map<String, Object>> planList = extractList(resp, "plans");
            if (planList.isEmpty()) {
                planList = extractList(resp, "application_plans");
            }
            List<ApplicationPlan> plans = new ArrayList<>();
            for (Map<String, Object> wrapper : planList) {
                Map<String, Object> planMap = wrapper;
                if (wrapper.get("application_plan") instanceof Map<?, ?> nested) {
                    planMap = (Map<String, Object>) nested;
                } else if (wrapper.get("plan") instanceof Map<?, ?> nested) {
                    planMap = (Map<String, Object>) nested;
                }
                ApplicationPlan plan = new ApplicationPlan();
                plan.id = String.valueOf(planMap.get("id"));
                plan.name = (String) planMap.get("name");
                plan.systemName = (String) planMap.get("system_name");
                plan.limits = fetchApplicationPlanLimits(client, plan.id, accessToken);
                plans.add(plan);
            }
            return plans;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch application plans for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchApplicationPlanLimits(
            ThreeScaleClient client, String planId, String accessToken) {
        try {
            Map<String, Object> resp = client.getApplicationPlanLimits(planId, accessToken);
            List<Map<String, Object>> limitList = extractList(resp, "limits");
            List<Map<String, Object>> limits = new ArrayList<>();
            for (Map<String, Object> wrapper : limitList) {
                Map<String, Object> limitMap = wrapper;
                if (wrapper.get("limit") instanceof Map<?, ?> nested) {
                    limitMap = (Map<String, Object>) nested;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("id", limitMap.get("id") != null ? String.valueOf(limitMap.get("id")) : null);
                normalized.put("metric_id", limitMap.get("metric_id") != null
                        ? String.valueOf(limitMap.get("metric_id")) : null);
                Object metricSystemName = limitMap.get("metric_system_name");
                if (metricSystemName == null) {
                    metricSystemName = limitMap.get("metric_name");
                }
                if (metricSystemName != null) {
                    normalized.put("metric_system_name", String.valueOf(metricSystemName));
                }
                Object period = limitMap.get("period");
                if (period != null) {
                    normalized.put("period", String.valueOf(period));
                }
                Object value = limitMap.get("value");
                if (value instanceof Number n) {
                    normalized.put("value", n.intValue());
                } else if (value != null) {
                    try {
                        normalized.put("value", Integer.parseInt(String.valueOf(value)));
                    } catch (NumberFormatException ignored) {
                        // skip unparsable values
                    }
                }
                if (normalized.containsKey("period") && normalized.containsKey("value")) {
                    limits.add(normalized);
                }
            }
            return limits;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch limits for application plan %s: %s", planId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> safeGetProxyConfig(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            return client.getProxyConfig(serviceId, accessToken);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch proxy config for service %s: %s", serviceId, e.getMessage());
            return null;
        }
    }

    private String extractProxyEndpoint(Map<String, Object> proxyConfig) {
        try {
            Map<String, Object> proxyConfigObj = (Map<String, Object>) proxyConfig.get("proxy_config");
            Map<String, Object> content = (Map<String, Object>) proxyConfigObj.get("content");
            Map<String, Object> proxy = (Map<String, Object>) content.get("proxy");
            return (String) proxy.get("endpoint");
        } catch (Exception e) {
            LOG.debugf("Failed to extract proxy endpoint from proxy config: %s", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> extractList(Map<String, Object> response, String key) {
        Object val = response.get(key);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return Collections.emptyList();
    }

    ThreeScaleClient buildClient(String baseUrl) {
        try {
            long connect = connectTimeoutSeconds;
            long read = Math.max(connectTimeoutSeconds * 2L, 15L);
            return RestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(connect, TimeUnit.SECONDS)
                    .readTimeout(read, TimeUnit.SECONDS)
                    .build(ThreeScaleClient.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid 3scale URL: " + baseUrl, e);
        }
    }
}
