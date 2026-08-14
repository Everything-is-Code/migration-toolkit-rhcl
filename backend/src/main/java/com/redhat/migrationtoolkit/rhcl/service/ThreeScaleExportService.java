package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.client.ThreeScaleClient;
import com.redhat.migrationtoolkit.rhcl.dto.ConnectionRequest;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Metric;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ThreeScaleExportService {

    private static final Logger LOG = Logger.getLogger(ThreeScaleExportService.class);

    public boolean testConnection(ConnectionRequest req) {
        try {
            ThreeScaleClient client = buildClient(req.url);
            Map<String, Object> result = client.getServices(req.accessToken, 1, 1);
            return result != null;
        } catch (Exception e) {
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
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(accountUrl))
                    .timeout(Duration.ofSeconds(5))
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
                    .timeout(Duration.ofSeconds(8))
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
            LOG.warnf("Failed to detect 3scale version: %s", e.getMessage());
        }
        return null;
    }

    public List<ApiService> exportServices(String url, String accessToken) {
        ThreeScaleClient client = buildClient(url);
        List<ApiService> services = new ArrayList<>();

        try {
            Map<String, Object> response = client.getServices(accessToken, 1, 500);
            List<Map<String, Object>> serviceList = extractList(response, "services");

            for (Map<String, Object> svcWrapper : serviceList) {
                Map<String, Object> svc = (Map<String, Object>) svcWrapper.get("service");
                if (svc == null) {
                    continue;
                }

                ApiService service = new ApiService();
                service.id = String.valueOf(svc.get("id"));
                service.name = (String) svc.get("name");
                service.description = (String) svc.get("description");
                service.state = (String) svc.get("state");
                service.systemName = (String) svc.get("system_name");
                service.backendVersion = (String) svc.get("backend_version");
                service.deploymentOption = (String) svc.get("deployment_option");

                service.policies = fetchPolicies(client, service.id, accessToken);
                service.mappingRules = fetchMappingRules(client, service.id, accessToken);
                service.metrics = fetchMetrics(client, service.id, accessToken);
                service.authentication = extractAuthentication(svc);
                service.backends = fetchBackendsForService(client, service.id, accessToken);
                service.applications = fetchApplications(client, service.id, accessToken);
                service.applicationPlans = fetchApplicationPlans(client, service.id, accessToken);

                Map<String, Object> proxyConfig = safeGetProxyConfig(client, service.id, accessToken);
                if (proxyConfig != null) {
                    service.proxyEndpoint = extractProxyEndpoint(proxyConfig);
                }

                services.add(service);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to export services from 3scale: " + e.getMessage(), e);
        }

        return services;
    }

    public ApiService exportService(String url, String accessToken, String serviceId) {
        ThreeScaleClient client = buildClient(url);
        Map<String, Object> response = client.getService(serviceId, accessToken);
        Map<String, Object> svc = (Map<String, Object>) response.get("service");

        ApiService service = new ApiService();
        service.id = String.valueOf(svc.get("id"));
        service.name = (String) svc.get("name");
        service.description = (String) svc.get("description");
        service.systemName = (String) svc.get("system_name");
        service.backendVersion = (String) svc.get("backend_version");
        service.deploymentOption = (String) svc.get("deployment_option");
        service.policies = fetchPolicies(client, serviceId, accessToken);
        service.mappingRules = fetchMappingRules(client, serviceId, accessToken);
        service.metrics = fetchMetrics(client, serviceId, accessToken);
        service.authentication = extractAuthentication(svc);
        service.backends = fetchBackendsForService(client, serviceId, accessToken);
        service.applications = fetchApplications(client, serviceId, accessToken);
        service.applicationPlans = fetchApplicationPlans(client, serviceId, accessToken);

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
            LOG.warnf("Failed to fetch policies for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MappingRule> fetchMappingRules(ThreeScaleClient client, String serviceId, String accessToken) {
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
            return Collections.emptyList();
        }
    }

    private List<Metric> fetchMetrics(ThreeScaleClient client, String serviceId, String accessToken) {
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
            return Collections.emptyList();
        }
    }

    private List<Backend> fetchBackendsForService(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            // backend_usages returns a JSON array directly (not wrapped in an object)
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
                    backends.add(backend);
                } catch (Exception ex) {
                    LOG.warnf("Failed to fetch backend %s: %s", backendId, ex.getMessage());
                }
            }
            return backends;
        } catch (Exception e) {
            LOG.warnf("Failed to fetch backend usages for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Authentication extractAuthentication(Map<String, Object> svc) {
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
     * Real credentials only — never invent keys.
     */
    @SuppressWarnings("unchecked")
    List<Application> fetchApplications(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            Map<String, Object> resp = client.getApplications(serviceId, accessToken, 1, 500);
            List<Map<String, Object>> appList = extractList(resp, "applications");
            if (appList.isEmpty()) {
                // Some tenants wrap differently or return empty — also try top-level list patterns
                Object raw = resp.get("applications");
                if (raw == null && resp.containsKey("application")) {
                    appList = List.of(resp);
                }
            }
            List<Application> applications = new ArrayList<>();
            for (Map<String, Object> wrapper : appList) {
                Map<String, Object> appMap = wrapper;
                if (wrapper.get("application") instanceof Map<?, ?> nested) {
                    appMap = (Map<String, Object>) nested;
                }
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
            return applications;
        } catch (Exception e) {
            LOG.warnf("Failed to fetch applications for service %s: %s", serviceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchApplicationKeys(ThreeScaleClient client, String applicationId, String accessToken) {
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
            LOG.warnf("Failed to fetch keys for application %s: %s", applicationId, e.getMessage());
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
            LOG.warnf("Failed to fetch application plans for service %s: %s", serviceId, e.getMessage());
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
            LOG.warnf("Failed to fetch limits for application plan %s: %s", planId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> safeGetProxyConfig(ThreeScaleClient client, String serviceId, String accessToken) {
        try {
            return client.getProxyConfig(serviceId, accessToken);
        } catch (Exception e) {
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
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> response, String key) {
        Object val = response.get(key);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return Collections.emptyList();
    }

    private ThreeScaleClient buildClient(String baseUrl) {
        try {
            return RestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .build(ThreeScaleClient.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid 3scale URL: " + baseUrl, e);
        }
    }
}
