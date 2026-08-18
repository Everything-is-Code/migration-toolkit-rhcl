package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ConversionService {

    private static final Logger LOG = Logger.getLogger(ConversionService.class);

    /**
     * Enum representing the backend type.
     * INTERNAL : Service within OpenShift/Kubernetes (no ServiceEntry, DestinationRule, or URLRewrite needed)
     * EXTERNAL : HTTPS endpoint outside the cluster (ServiceEntry, DestinationRule, and URLRewrite required)
     */
    enum BackendType { INTERNAL, EXTERNAL }

    public Map<String, String> convert(ApiService service, String namespace) {
        return convert(service, namespace, null, new ConversionOptions());
    }

    public Map<String, String> convert(ApiService service, String namespace, String backendUrl) {
        return convert(service, namespace, backendUrl, new ConversionOptions());
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        opts.anonymousTarget = anonymousTarget;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget,
            boolean includeMigratedFromLabel) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        opts.anonymousTarget = anonymousTarget;
        opts.includeMigratedFromLabel = includeMigratedFromLabel;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, ConversionOptions opts) {
        if (opts == null) {
            opts = new ConversionOptions();
        }
        String loggingTarget = opts.loggingTarget != null ? opts.loggingTarget : "gateway";
        String anonymousTarget = opts.anonymousTarget != null ? opts.anonymousTarget : "httproute";
        boolean includeMigratedFromLabel = opts.includeMigratedFromLabel;
        String ipCheckMode = "authPolicyOpa".equals(opts.ipCheckMode)
                ? "authPolicyOpa" : "authorizationPolicy";

        Map<String, String> files = new LinkedHashMap<>();
        String name = toKebabCase(service.systemName != null ? service.systemName : service.name);

        // If the user did not explicitly specify a URL, auto-detect the type
        // using the actual backend (privateEndpoint) registered in 3scale.
        // By aligning with the configmap.yaml fallback, this prevents
        // inconsistencies such as "the backend URL points to an ELB but the
        // HTTPRoute points to a non-existent internal cluster Service".
        String effectiveBackendUrl = backendUrl;
        if ((effectiveBackendUrl == null || effectiveBackendUrl.isBlank())
                && service.backends != null && !service.backends.isEmpty()) {
            effectiveBackendUrl = service.backends.get(0).privateEndpoint;
        }

        BackendType backendType = detectBackendType(effectiveBackendUrl);
        String externalHost = backendType == BackendType.EXTERNAL ? extractHostname(effectiveBackendUrl) : null;
        String internalService = backendType == BackendType.INTERNAL
                ? extractInternalService(effectiveBackendUrl, name) : null;
        int internalPort = backendType == BackendType.INTERNAL
                ? extractPort(effectiveBackendUrl, ConversionConstants.DEFAULT_INTERNAL_PORT)
                : ConversionConstants.DEFAULT_INTERNAL_PORT;
        // The external backend port is determined from the URL scheme (http→80 / https→443)
        // or an explicitly specified port. Hardcoding 443 would prevent connections to
        // backends that only listen on HTTP (e.g., OpenShift Routes without TLS).
        int externalDefaultPort = effectiveBackendUrl != null && effectiveBackendUrl.trim().startsWith("http://")
                ? ConversionConstants.DEFAULT_HTTP_PORT : ConversionConstants.DEFAULT_HTTPS_PORT;
        int externalPort = backendType == BackendType.EXTERNAL
                ? extractPort(effectiveBackendUrl, externalDefaultPort)
                : ConversionConstants.DEFAULT_HTTPS_PORT;

        files.put("gateway.yaml", generateGateway(name, namespace));
        files.put("httproute.yaml",  generateHttpRoute(
                name, namespace, service, backendType, externalHost, internalService,
                internalPort, externalPort, opts.corsNative));
        files.put("policy.yaml",     generateAuthPolicy(name, namespace, service, anonymousTarget, ipCheckMode));
        files.put("secret.yaml",     generateSecret(name, namespace, service));
        files.put("configmap.yaml",  generateConfigMap(name, namespace, service, effectiveBackendUrl));
        files.put("apiproduct.yaml", generateApiProduct(name, namespace, service));

        String authType = service.authentication != null ? service.authentication.type : "none";
        if ("apiKey".equals(authType)) {
            files.put("apikey.yaml", generateApiKey(name, namespace));
        }

        if (backendType == BackendType.EXTERNAL) {
            boolean externalUsesTls = externalPort == ConversionConstants.DEFAULT_HTTPS_PORT;
            files.put("serviceentry.yaml",
                    generateServiceEntry(name, namespace, externalHost, externalPort, externalUsesTls));
            files.put("destinationrule.yaml",
                    generateDestinationRule(name, namespace, externalHost, externalUsesTls));
        }

        Policy loggingPolicy = findLoggingPolicy(service);
        if (loggingPolicy != null) {
            boolean isGateway = !"workload".equals(loggingTarget);
            files.put("telemetry.yaml", generateTelemetry(name, namespace, loggingPolicy, isGateway));
            List<Map<String, Object>> jsonCfgCheck =
                    parseJsonObjectConfig(loggingPolicy.configuration != null
                            ? loggingPolicy.configuration.get("json_object_config") : null);
            if (!jsonCfgCheck.isEmpty()) {
                files.put("envoyfilter-logging.yaml",
                        generateLoggingEnvoyFilter(name, namespace, jsonCfgCheck, isGateway));
            }
        }

        Policy urlRewritingPolicy = findUrlRewritingPolicy(service);
        if (urlRewritingPolicy != null) {
            List<Map<String, Object>> rewriteCommands = parseJsonObjectConfig(
                    urlRewritingPolicy.configuration != null
                            ? urlRewritingPolicy.configuration.get("commands") : null);
            if (!rewriteCommands.isEmpty()) {
                files.put("envoyfilter-url-rewriting.yaml",
                        generateUrlRewritingEnvoyFilter(name, namespace, rewriteCommands));
            }
        }

        Policy ipCheck = findIpCheckPolicy(service);
        if (ipCheck != null && "authorizationPolicy".equals(ipCheckMode)) {
            files.put("authorizationpolicy.yaml",
                    generateAuthorizationPolicy(name, namespace, ipCheck));
        }

        String rateLimitYaml = generateRateLimitPolicy(name, namespace, service);
        if (rateLimitYaml != null) {
            files.put("ratelimitpolicy.yaml", rateLimitYaml);
        }

        if (opts.includeTlsPolicy) {
            files.put("tlspolicy.yaml",
                    generateTlsPolicy(name, namespace, opts.tlsIssuerKind, opts.tlsIssuerName));
        }

        files.put("README.md", generateReadme(service, name, namespace, backendType, externalHost));

        if (!includeMigratedFromLabel) {
            files.replaceAll((fileName, content) -> stripMigratedFromLabel(content));
        }
        return files;
    }

    /** Remove the "migrated-from: 3scale" label line from the generated YAML (when disabled via checkbox). */
    private String stripMigratedFromLabel(String content) {
        return content.replaceAll("(?m)^[ \\t]*migrated-from: 3scale\\R?", "");
    }

    // ─────────────────────────────────────────────
    // Backend type detection
    // ─────────────────────────────────────────────

    /**
     * Detect the backend type from the URL.
     *   null / empty string                       → INTERNAL (default)
     *   *.svc / *.svc.cluster.local format        → INTERNAL
     *   In-cluster DNS (hostname without dots)     → INTERNAL
     *   https?://external...                       → EXTERNAL
     */
    BackendType detectBackendType(String url) {
        if (url == null || url.isBlank()) {
            return BackendType.INTERNAL;
        }
        String host = extractHostname(url);
        if (host == null) {
            return BackendType.INTERNAL;
        }
        // *.svc or *.svc.cluster.local → internal
        if (host.endsWith(".svc") || host.endsWith(".svc.cluster.local")) {
            return BackendType.INTERNAL;
        }
        // Simple hostname without dots (e.g., my-service) → internal
        if (!host.contains(".")) {
            return BackendType.INTERNAL;
        }
        return BackendType.EXTERNAL;
    }

    /** Extract the hostname from a URL. Returns null on failure. */
    private String extractHostname(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "https://" + s;
            }
            return new URI(s).getHost();
        } catch (Exception e) {
            LOG.debugf("Failed to extract hostname from URL '%s': %s", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extract the service name from an internal backend URL.
     * "http://my-service:8080" → "my-service"
     * Falls back to "{name}-backend" if extraction fails.
     */
    private String extractInternalService(String url, String name) {
        String host = extractHostname(url);
        if (host == null || host.isBlank()) {
            return name + "-backend";
        }
        // Strip the "svc.cluster.local" suffix and return only the leading service name
        return host.split("\\.")[0];
    }

    /** Extract the port number from a URL. Returns the default value on failure. */
    private int extractPort(String url, int defaultPort) {
        if (url == null || url.isBlank()) {
            return defaultPort;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "http://" + s;
            }
            int port = new URI(s).getPort();
            return port > 0 ? port : defaultPort;
        } catch (Exception e) {
            LOG.debugf("Failed to extract port from URL '%s': %s", url, e.getMessage());
            return defaultPort;
        }
    }

    // ─────────────────────────────────────────────
    // Gateway
    // ─────────────────────────────────────────────

    private String generateGateway(String name, String namespace) {
        return """
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: %s-gateway
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  gatewayClassName: istio
  listeners:
    - name: http
      protocol: HTTP
      port: %d
      allowedRoutes:
        namespaces:
          from: Same
    - name: https
      protocol: HTTPS
      port: %d
      tls:
        mode: Terminate
        certificateRefs:
          - name: %s-tls
      allowedRoutes:
        namespaces:
          from: Same
""".formatted(name, namespace, name,
                ConversionConstants.DEFAULT_HTTP_PORT,
                ConversionConstants.DEFAULT_HTTPS_PORT,
                name);
    }

    // ─────────────────────────────────────────────
    // TLSPolicy (Kuadrant + cert-manager)
    // ─────────────────────────────────────────────

    private String generateTlsPolicy(String name, String namespace,
                                     String issuerKind, String issuerName) {
        String kind = (issuerKind != null && !issuerKind.isBlank()) ? issuerKind : "ClusterIssuer";
        String issuer = (issuerName != null && !issuerName.isBlank()) ? issuerName : "letsencrypt-prod";
        return """
apiVersion: kuadrant.io/v1
kind: TLSPolicy
metadata:
  name: %s-tls-policy
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: %s-gateway
  issuerRef:
    group: cert-manager.io
    kind: %s
    name: %s
""".formatted(name, namespace, name, name, kind, issuer);
    }

    // ─────────────────────────────────────────────
    // HTTPRoute
    // ─────────────────────────────────────────────

    private String generateHttpRoute(String name, String namespace, ApiService service,
                                     BackendType backendType, String externalHost,
                                     String internalService, int internalPort, int externalPort,
                                     boolean corsNative) {
        int backendPort   = backendType == BackendType.EXTERNAL ? externalPort : internalPort;
        String backendSvc = backendType == BackendType.EXTERNAL
                ? (name + "-backend")
                : (internalService != null ? internalService : name + "-backend");

        // Build the filters block (combining URLRewrite + Header Modification + CORS)
        StringBuilder filterItems = new StringBuilder();
        if (backendType == BackendType.EXTERNAL) {
            filterItems.append("""
        - type: URLRewrite
          urlRewrite:
            hostname: "%s"
""".formatted(externalHost));
        }
        filterItems.append(buildHeaderModificationFilters(service));
        filterItems.append(buildCorsFilters(service, corsNative));

        String filtersBlock = filterItems.length() > 0
                ? "      filters:\n" + filterItems
                : "";

        String annotations = buildUpstreamAnnotations(service);
        StringBuilder sb = new StringBuilder();
        sb.append("""
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: %s-route
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
%sspec:
  parentRefs:
    - name: %s-gateway
      namespace: %s
      sectionName: http
  rules:
""".formatted(name, namespace, name, annotations, name, namespace));

        String timeoutsBlock = buildTimeoutsBlock(service);
        boolean hasCors = findCorsPolicy(service) != null;
        LinkedHashSet<String> pathsForOptions = new LinkedHashSet<>();

        if (service.mappingRules != null && !service.mappingRules.isEmpty()) {
            // For HTTP methods that already have a "/" (catch-all) Mapping Rule,
            // skip any subsequent rules for the same method since they are always subsumed by "/".
            Set<String> catchAllMethods = new HashSet<>();
            Set<String> emitted = new LinkedHashSet<>();
            for (MappingRule rule : service.mappingRules) {
                String path   = toGatewayApiPathPrefix(rule.pattern);
                String method = rule.httpMethod != null ? rule.httpMethod : "GET";

                if (catchAllMethods.contains(method) || !emitted.add(path + " " + method)) {
                    continue;
                }
                if ("/".equals(path)) {
                    catchAllMethods.add(method);
                }
                pathsForOptions.add(path);

                sb.append("""
    - matches:
        - path:
            type: PathPrefix
            value: "%s"
          method: %s
%s%s      backendRefs:
        - name: %s
          port: %d
""".formatted(path, method, filtersBlock, timeoutsBlock, backendSvc, backendPort));
            }
        } else {
            pathsForOptions.add("/");
            sb.append("""
    - matches:
        - path:
            type: PathPrefix
            value: "/"
%s%s      backendRefs:
        - name: %s
          port: %d
""".formatted(filtersBlock, timeoutsBlock, backendSvc, backendPort));
        }

        // CORS preflight: OPTIONS on product path(s) when cors policy is enabled
        if (hasCors) {
            Set<String> emittedOptions = new HashSet<>();
            if (service.mappingRules != null) {
                for (MappingRule rule : service.mappingRules) {
                    if (rule.httpMethod != null && "OPTIONS".equalsIgnoreCase(rule.httpMethod)) {
                        emittedOptions.add(toGatewayApiPathPrefix(rule.pattern));
                    }
                }
            }
            for (String path : pathsForOptions) {
                if (!emittedOptions.add(path)) {
                    continue;
                }
                sb.append("""
    - matches:
        - path:
            type: PathPrefix
            value: "%s"
          method: OPTIONS
%s%s      backendRefs:
        - name: %s
          port: %d
""".formatted(path, filtersBlock, timeoutsBlock, backendSvc, backendPort));
            }
        }
        return sb.toString();
    }

    /**
     * Convert a 3scale Mapping Rule pattern (e.g., "/api/dashboard/{id}", "/foo/{?}")
     * into a value usable as a Gateway API PathPrefix.
     * Gateway API path.value only allows `^(?:[-A-Za-z0-9/._~!$&'()*+,;=:@]|[%][0-9a-fA-F]{2})+$`
     * and cannot contain templated path parameters with `{`/`}`.
     * Therefore, only the portion up to the first path parameter is used as the PathPrefix
     * (e.g., "/api/dashboard/{id}" → "/api/dashboard").
     */
    private String toGatewayApiPathPrefix(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "/";
        }
        int braceIdx = pattern.indexOf('{');
        String prefix = braceIdx >= 0 ? pattern.substring(0, braceIdx) : pattern;
        if (prefix.length() > 1 && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix.isBlank() ? "/" : prefix;
    }

    @SuppressWarnings("unchecked")
    private String buildHeaderModificationFilters(ApiService service) {
        if (service.policies == null) {
            return "";
        }
        Policy policy = service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && isHeaderModificationPolicy(p.name))
                .findFirst().orElse(null);
        if (policy == null || policy.configuration == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (String direction : new String[]{"response", "request"}) {
            Object raw = policy.configuration.get(direction);
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                continue;
            }

            StringBuilder setHeaders    = new StringBuilder();
            StringBuilder addHeaders    = new StringBuilder();
            StringBuilder removeHeaders = new StringBuilder();

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object hRaw = entry.get("header");
                Object vRaw = entry.get("value");
                Object oRaw = entry.get("op");
                Object tRaw = entry.get("value_type");
                String headerRaw = (hRaw != null ? hRaw.toString() : "").replace(":", "").trim();
                String value     = vRaw != null ? vRaw.toString() : "";
                String op        = oRaw != null ? oRaw.toString() : "push";
                String valueType = tRaw != null ? tRaw.toString() : "plain";

                if (headerRaw.isBlank()) {
                    continue;
                }

                if ("liquid".equals(valueType)) {
                    result.append(String.format(
                            "        # Header '%s' uses liquid template — manual conversion required: %s%n",
                            headerRaw, value));
                    continue;
                }

                String headerLine = String.format(
                        "              - name: %s%n                value: \"%s\"%n", headerRaw, value);
                switch (op) {
                    case "add"    -> addHeaders.append(headerLine);
                    case "delete" -> removeHeaders.append(
                            String.format("              - %s%n", headerRaw));
                    default       -> setHeaders.append(headerLine);
                }
            }

            boolean hasAny = setHeaders.length() > 0
                    || addHeaders.length() > 0
                    || removeHeaders.length() > 0;
            if (!hasAny) {
                continue;
            }

            String filterType  = "response".equals(direction)
                    ? "ResponseHeaderModifier" : "RequestHeaderModifier";
            String modifierKey = "response".equals(direction)
                    ? "responseHeaderModifier" : "requestHeaderModifier";

            StringBuilder modifier = new StringBuilder();
            if (setHeaders.length() > 0) {
                modifier.append("            set:\n").append(setHeaders);
            }
            if (addHeaders.length() > 0) {
                modifier.append("            add:\n").append(addHeaders);
            }
            if (removeHeaders.length() > 0) {
                modifier.append("            remove:\n").append(removeHeaders);
            }

            result.append(String.format(
                    "        - type: %s%n          %s:%n%s",
                    filterType, modifierKey, modifier));
        }

        return result.toString();
    }

    /** True when policy name is {@code headers} or alias {@code header_modification} (case-insensitive). */
    private static boolean isHeaderModificationPolicy(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        return "headers".equals(n) || "header_modification".equals(n);
    }

    private Policy findCorsPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && "cors".equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Maps 3scale {@code cors} to Gateway API CORS handling.
     * <ul>
     *   <li>{@code corsNative=true}: emit {@code type: CORS} (Gateway API ≥ 1.3 / OCP ≥ 4.21)</li>
     *   <li>{@code corsNative=false} (default): ResponseHeaderModifier Access-Control-*
     *       (+ OPTIONS rules elsewhere), matching migration-pilot {@code pilot_cors_ip}</li>
     * </ul>
     */
    private String buildCorsFilters(ApiService service, boolean corsNative) {
        Policy cors = findCorsPolicy(service);
        if (cors == null || cors.configuration == null) {
            return "";
        }
        Map<String, Object> cfg = cors.configuration;

        List<String> originList = toStringList(cfg.get("allow_origin")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        List<String> methodList = toStringList(cfg.get("allow_methods")).stream()
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .toList();
        List<String> headerList = toStringList(cfg.get("allow_headers")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        boolean credentials = Boolean.TRUE.equals(cfg.get("allow_credentials"))
                || "true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("allow_credentials", "false")));
        Object maxAgeRaw = cfg.get("max_age");
        Integer maxAge = null;
        if (maxAgeRaw instanceof Number n) {
            maxAge = n.intValue();
        } else if (maxAgeRaw != null) {
            try {
                maxAge = Integer.parseInt(maxAgeRaw.toString().trim());
            } catch (NumberFormatException ignored) {
                maxAge = null;
            }
        }

        if (corsNative) {
            return buildNativeCorsFilter(originList, methodList, headerList, credentials, maxAge);
        }
        return buildCorsResponseHeaderModifier(originList, methodList, headerList, credentials, maxAge);
    }

    private static String buildNativeCorsFilter(List<String> originList,
                                                List<String> methodList,
                                                List<String> headerList,
                                                boolean credentials,
                                                Integer maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append("        - type: CORS\n");
        sb.append("          cors:\n");
        sb.append("            allowOrigins:\n");
        if (originList.isEmpty()) {
            sb.append("              - ").append(yamlDoubleQuoted("*")).append('\n');
        } else {
            for (String origin : originList) {
                // Bare "*" is a YAML alias indicator and fails parse; always quote origins.
                sb.append("              - ").append(yamlDoubleQuoted(origin)).append('\n');
            }
        }
        if (!methodList.isEmpty()) {
            sb.append("            allowMethods:\n");
            for (String method : methodList) {
                sb.append("              - ").append(method).append('\n');
            }
        }
        if (!headerList.isEmpty()) {
            sb.append("            allowHeaders:\n");
            for (String header : headerList) {
                sb.append("              - ").append(yamlDoubleQuoted(header)).append('\n');
            }
        }
        if (credentials) {
            sb.append("            allowCredentials: true\n");
        }
        if (maxAge != null) {
            sb.append("            maxAge: ").append(maxAge).append('\n');
        }
        return sb.toString();
    }

    private static String buildCorsResponseHeaderModifier(List<String> originList,
                                                          List<String> methodList,
                                                          List<String> headerList,
                                                          boolean credentials,
                                                          Integer maxAge) {
        String allowOrigin = "*";
        if (!originList.isEmpty()) {
            allowOrigin = originList.stream().anyMatch("*"::equals) ? "*" : originList.get(0);
        }

        StringBuilder setHeaders = new StringBuilder();
        setHeaders.append(String.format(
                "              - name: Access-Control-Allow-Origin%n                value: %s%n",
                yamlDoubleQuoted(allowOrigin)));
        if (!methodList.isEmpty()) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Methods%n                value: %s%n",
                    yamlDoubleQuoted(String.join(", ", methodList))));
        }
        if (!headerList.isEmpty()) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Headers%n                value: %s%n",
                    yamlDoubleQuoted(String.join(", ", headerList))));
        }
        if (credentials) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Credentials%n                value: \"true\"%n"));
        }
        if (maxAge != null) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Max-Age%n                value: \"%d\"%n",
                    maxAge));
        }

        return "        - type: ResponseHeaderModifier\n"
                + "          responseHeaderModifier:\n"
                + "            set:\n"
                + setHeaders;
    }

    /** Double-quote a YAML scalar so values like {@code *} do not parse as aliases. */
    static String yamlDoubleQuoted(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<String> toStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return out;
        }
        // 3scale sometimes stores space/comma/newline separated origins
        for (String part : s.split("[,\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    /**
     * Convert the upstream_connection policy timeout values to Gateway API timeouts fields.
     *   connect_timeout → backendRequest (backend connection timeout)
     *   read_timeout    → request (response receive timeout)
     *   send_timeout    → recorded as annotation (no direct Gateway API field)
     */
    private String buildTimeoutsBlock(ApiService service) {
        if (service.policies == null) {
            return "";
        }
        for (Policy p : service.policies) {
            if (!"upstream_connection".equals(p.name)) {
                continue;
            }
            if (!Boolean.TRUE.equals(p.enabled)) {
                continue;
            }
            if (p.configuration == null) {
                continue;
            }

            Object connectRaw = p.configuration.get("connect_timeout");
            Object sendRaw    = p.configuration.get("send_timeout");
            Object readRaw    = p.configuration.get("read_timeout");

            if (connectRaw == null && sendRaw == null && readRaw == null) {
                return "";
            }

            StringBuilder block = new StringBuilder("      timeouts:\n");
            if (readRaw != null) {
                block.append(String.format("        request: \"%ss\"  # read_timeout%n", readRaw));
            }
            if (connectRaw != null) {
                block.append(String.format("        backendRequest: \"%ss\"  # connect_timeout%n", connectRaw));
            }
            if (sendRaw != null) {
                block.append(String.format(
                    "        # send_timeout: %ss  (no direct Gateway API mapping — see annotations)%n", sendRaw));
            }
            return block.toString();
        }
        return "";
    }

    /**
     * Return the upstream_connection send_timeout as an annotation (attached to HTTPRoute metadata).
     */
    private String buildUpstreamAnnotations(ApiService service) {
        if (service.policies == null) {
            return "";
        }
        for (Policy p : service.policies) {
            if (!"upstream_connection".equals(p.name)) {
                continue;
            }
            if (!Boolean.TRUE.equals(p.enabled)) {
                continue;
            }
            if (p.configuration == null) {
                continue;
            }
            Object sendRaw = p.configuration.get("send_timeout");
            if (sendRaw == null) {
                return "";
            }
            return """
  annotations:
    3scale-migration/upstream-send-timeout: "%ss"
""".formatted(sendRaw);
        }
        return "";
    }

    // ─────────────────────────────────────────────
    // ServiceEntry (generated only for external backends)
    // ─────────────────────────────────────────────

    private String generateServiceEntry(String name, String namespace, String externalHost,
                                        int externalPort, boolean useTls) {
        String backendSvc = name + "-backend";
        String portName = useTls ? "https" : "http";
        String protocol = useTls ? "HTTPS" : "HTTP";
        return """
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: %s-external
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  hosts:
  - %s
  ports:
  - number: %d
    name: %s
    protocol: %s
  resolution: DNS
  location: MESH_EXTERNAL
---
apiVersion: v1
kind: Service
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  type: ExternalName
  externalName: %s
  ports:
  - name: %s
    port: %d
""".formatted(name, namespace, name, externalHost, externalPort, portName, protocol,
              backendSvc, namespace, name, externalHost, portName, externalPort);
    }

    // ─────────────────────────────────────────────
    // DestinationRule (generated only for external backends)
    // ─────────────────────────────────────────────

    private String generateDestinationRule(String name, String namespace, String externalHost, boolean useTls) {
        String trafficPolicy = useTls
                ? """
  trafficPolicy:
    tls:
      mode: SIMPLE
      sni: %s
""".formatted(externalHost)
                : """
  trafficPolicy:
    tls:
      mode: DISABLE
""";
        return """
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: %s-backend-tls
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  host: %s
%s""".formatted(name, namespace, name, externalHost, trafficPolicy);
    }

    // ─────────────────────────────────────────────
    // AuthPolicy
    // ─────────────────────────────────────────────

    private String generateAuthPolicy(String name, String namespace, ApiService service,
            String anonymousTarget, String ipCheckMode) {
        String authType = service.authentication != null ? service.authentication.type : "none";

        // Anonymous Access (default_credentials policy) — inject credentials as response headers
        Policy anonymousPolicy = findAnonymousPolicy(service);
        if (anonymousPolicy != null) {
            String yaml = generateAnonymousAuthPolicy(name, namespace, anonymousPolicy, anonymousTarget);
            return finalizeAuthPolicyAuthorization(yaml, service, ipCheckMode);
        }

        // token_introspection → AuthPolicy oauth2Introspection (GateForge #202 / Kuadrant)
        Policy tokenIntrospection = findTokenIntrospectionPolicy(service);
        if (tokenIntrospection != null) {
            String introspectionYaml = generateOauth2IntrospectionAuthPolicy(
                    name, namespace, tokenIntrospection, buildAuthCacheBlock(findAuthCachingPolicy(service)));
            if (introspectionYaml != null) {
                return finalizeAuthPolicyAuthorization(introspectionYaml, service, ipCheckMode);
            }
            // Incomplete (no URL): fall through to normal auth; warning emitted via README/secret
        }

        String authCacheBlock = buildAuthCacheBlock(findAuthCachingPolicy(service));

        if ("jwt".equals(authType)) {
            String issuer = service.authentication.oidcIssuerEndpoint != null
                    ? service.authentication.oidcIssuerEndpoint
                    : ConversionConstants.DEFAULT_OIDC_ISSUER_URL;
            String yaml = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      jwt-auth:
        jwt:
          issuerUrl: %s
%s""".formatted(name, namespace, name, name, issuer, authCacheBlock);
            return finalizeAuthPolicyAuthorization(yaml, service, ipCheckMode);
        } else if ("apiKey".equals(authType)) {
            String yaml = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      api-key-auth:
        apiKey:
          selector:
            matchLabels:
              app: %s
%s        credentials:
          authorizationHeader:
            prefix: APIKEY
""".formatted(name, namespace, name, name, name, authCacheBlock);
            return finalizeAuthPolicyAuthorization(yaml, service, ipCheckMode);
        } else if ("appIdKey".equals(authType)) {
            String yaml = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/auth-type: "app-id-key"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      app-id-key-auth:
        apiKey:
          selector:
            matchLabels:
              app: %s
              auth-type: app-id-key
%s        credentials:
          queryString:
            name: app_key
""".formatted(name, namespace, name, name, name, authCacheBlock);
            return finalizeAuthPolicyAuthorization(yaml, service, ipCheckMode);
        }

        String yaml = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication: {}
""".formatted(name, namespace, name, name);
        return finalizeAuthPolicyAuthorization(yaml, service, ipCheckMode);
    }

    /** Append jwt_claim_check patternMatching, then optional ip_check OPA. */
    private String finalizeAuthPolicyAuthorization(String authPolicyYaml, ApiService service,
                                                    String ipCheckMode) {
        return appendIpCheckOpaIfNeeded(
                appendJwtClaimCheckAuthorization(authPolicyYaml, service), service, ipCheckMode);
    }

    /**
     * When ipCheckMode is authPolicyOpa, append OPA authorization encoding the same
     * allow/deny CIDR logic that AuthorizationPolicy would enforce.
     * Merges with any existing authorization siblings (e.g. jwt-claim-check).
     */
    private String appendIpCheckOpaIfNeeded(String authPolicyYaml, ApiService service, String ipCheckMode) {
        if (!"authPolicyOpa".equals(ipCheckMode)) {
            return authPolicyYaml;
        }
        Policy ipCheck = findIpCheckPolicy(service);
        if (ipCheck == null) {
            return authPolicyYaml;
        }
        String opaBlock = buildIpCheckOpaAuthorization(ipCheck);
        if (opaBlock.isEmpty()) {
            return authPolicyYaml;
        }
        return mergeAuthorizationNamedRules(authPolicyYaml, opaBlock);
    }

    private record JwtClaimPattern(String selector, String operator, String value) {}

    private record JwtClaimParseResult(List<JwtClaimPattern> patterns, List<String> gapNotes) {}

    private Policy findJwtClaimCheckPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && "jwt_claim_check".equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Parse 3scale jwt_claim_check rules into Authorino patternMatching patterns.
     * Skips liquid / combine_op=or / unknown ops (gap notes); path-scoped rules still emit
     * global patterns with a path-scope gap note.
     */
    @SuppressWarnings("unchecked")
    private JwtClaimParseResult parseJwtClaimCheckRules(Policy policy) {
        List<JwtClaimPattern> patterns = new ArrayList<>();
        List<String> gapNotes = new ArrayList<>();
        if (policy == null || policy.configuration == null) {
            return new JwtClaimParseResult(patterns, gapNotes);
        }
        Object rulesRaw = policy.configuration.get("rules");
        if (!(rulesRaw instanceof List<?> rules)) {
            return new JwtClaimParseResult(patterns, gapNotes);
        }
        if (Boolean.TRUE.equals(policy.configuration.get("enable_extended_context"))) {
            gapNotes.add("enable_extended_context is not converted — claim checks use plain JWT identity only");
        }
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) ruleMap;
            String combineOp = String.valueOf(rule.getOrDefault("combine_op", "and")).trim().toLowerCase(Locale.ROOT);
            if ("or".equals(combineOp)) {
                gapNotes.add("combine_op=or is not supported — Authorino authorization rules are AND across patterns; OR rule skipped");
                continue;
            }
            String resourceType = String.valueOf(rule.getOrDefault("resource_type", "plain")).trim().toLowerCase(Locale.ROOT);
            if ("liquid".equals(resourceType)) {
                gapNotes.add("resource_type=liquid is not converted — path gating ignored; claim patterns may still apply globally");
            }
            if (!isCatchAllJwtClaimResource(rule)) {
                gapNotes.add("path/method-gated jwt_claim_check rules are applied globally in AuthPolicy (no Authorino when/path scoping in P1)");
            }
            Object opsRaw = rule.get("operations");
            if (!(opsRaw instanceof List<?> ops)) {
                continue;
            }
            for (Object opObj : ops) {
                if (!(opObj instanceof Map<?, ?> opMap)) {
                    continue;
                }
                Map<String, Object> op = (Map<String, Object>) opMap;
                String claimType = String.valueOf(op.getOrDefault("jwt_claim_type", "plain")).trim().toLowerCase(Locale.ROOT);
                String valueType = String.valueOf(op.getOrDefault("value_type", "plain")).trim().toLowerCase(Locale.ROOT);
                if ("liquid".equals(claimType) || "liquid".equals(valueType)) {
                    gapNotes.add("liquid jwt_claim/value is not converted — operation skipped");
                    continue;
                }
                Object claimRaw = op.get("jwt_claim");
                if (claimRaw == null || claimRaw.toString().isBlank()) {
                    continue;
                }
                String claim = claimRaw.toString().trim();
                String threeScaleOp = String.valueOf(op.getOrDefault("op", "")).trim();
                String authorinoOp = mapJwtClaimOp(threeScaleOp);
                if (authorinoOp == null) {
                    gapNotes.add("unsupported jwt_claim_check op '" + threeScaleOp + "' — skipped");
                    continue;
                }
                Object valueRaw = op.get("value");
                String value = valueRaw != null ? valueRaw.toString() : "";
                patterns.add(new JwtClaimPattern("auth.identity." + claim, authorinoOp, value));
            }
        }
        return new JwtClaimParseResult(patterns, gapNotes);
    }

    private static String mapJwtClaimOp(String threeScaleOp) {
        return switch (threeScaleOp) {
            case "==" -> "eq";
            case "!=" -> "neq";
            case "matches" -> "matches";
            default -> null;
        };
    }

    private static boolean isCatchAllJwtClaimResource(Map<String, Object> rule) {
        String resource = rule.get("resource") != null ? rule.get("resource").toString().trim() : "";
        boolean resourceOk = resource.isEmpty() || "/".equals(resource) || ".*".equals(resource);
        List<String> methods = toStringList(rule.get("methods"));
        boolean methodsOk = methods.isEmpty()
                || methods.stream().anyMatch(m -> "ANY".equalsIgnoreCase(m));
        return resourceOk && methodsOk;
    }

    private String buildJwtClaimCheckNamedRule(List<JwtClaimPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("      jwt-claim-check:\n");
        sb.append("        patternMatching:\n");
        sb.append("          patterns:\n");
        for (JwtClaimPattern pattern : patterns) {
            sb.append("            - selector: ").append(pattern.selector()).append('\n');
            sb.append("              operator: ").append(pattern.operator()).append('\n');
            sb.append("              value: ").append(yamlDoubleQuoted(pattern.value())).append('\n');
        }
        return sb.toString();
    }

    private String appendJwtClaimCheckAuthorization(String authPolicyYaml, ApiService service) {
        Policy claimCheck = findJwtClaimCheckPolicy(service);
        if (claimCheck == null) {
            return authPolicyYaml;
        }
        JwtClaimParseResult parsed = parseJwtClaimCheckRules(claimCheck);
        String namedRule = buildJwtClaimCheckNamedRule(parsed.patterns());
        if (namedRule.isEmpty()) {
            return authPolicyYaml;
        }
        return mergeAuthorizationNamedRules(authPolicyYaml, namedRule);
    }

    /**
     * Merge a named authorization rule body (indented under {@code authorization:}) into AuthPolicy YAML.
     * Creates the {@code authorization:} map when missing; otherwise inserts as a sibling entry.
     */
    private String mergeAuthorizationNamedRules(String authPolicyYaml, String namedRuleBlock) {
        if (authPolicyYaml == null || authPolicyYaml.isBlank()
                || namedRuleBlock == null || namedRuleBlock.isBlank()) {
            return authPolicyYaml;
        }
        String block = namedRuleBlock;
        if (block.startsWith("    authorization:\n")) {
            block = block.substring("    authorization:\n".length());
        }
        if (!block.endsWith("\n")) {
            block = block + "\n";
        }
        String marker = "\n    authorization:";
        int authIdx = authPolicyYaml.indexOf(marker);
        if (authIdx < 0) {
            return authPolicyYaml.stripTrailing() + "\n    authorization:\n" + block;
        }
        // Insert named rule as first child under existing authorization map
        int insertAt = authIdx + marker.length();
        // skip to end of "authorization:" line
        int lineEnd = authPolicyYaml.indexOf('\n', insertAt);
        if (lineEnd < 0) {
            return authPolicyYaml + "\n" + block;
        }
        return authPolicyYaml.substring(0, lineEnd + 1) + block + authPolicyYaml.substring(lineEnd + 1);
    }

    private String buildIpCheckOpaAuthorization(Policy ipCheck) {
        Map<String, Object> cfg = ipCheck.configuration != null ? ipCheck.configuration : Map.of();
        String checkType = String.valueOf(cfg.getOrDefault("check_type", "whitelist"));
        List<String> ips = toStringList(cfg.get("ips"));
        if (ips.isEmpty()) {
            return "";
        }
        StringBuilder cidrList = new StringBuilder();
        for (String ip : ips) {
            String cidr = normalizeCidr(ip);
            if (cidr == null) {
                continue;
            }
            if (cidrList.length() > 0) {
                cidrList.append(", ");
            }
            cidrList.append("\"").append(cidr).append("\"");
        }
        if (cidrList.length() == 0) {
            return "";
        }
        boolean whitelist = !"blacklist".equalsIgnoreCase(checkType)
                && !"deny".equalsIgnoreCase(checkType);
        // whitelist: allow if IP in list; blacklist: allow if IP NOT in list
        String allowBody = whitelist
                ? """
            allow {
              some i
              net.cidr_contains(cidrs[i], client_ip)
            }
"""
                : """
            allow {
              not denied
            }
            denied {
              some i
              net.cidr_contains(cidrs[i], client_ip)
            }
""";
        return """
    authorization:
      ip-check:
        opa:
          rego: |
            package ipcheck
            import future.keywords
            cidrs := [%s]
            client_ip := input.attributes.source.address
%s""".formatted(cidrList, allowBody);
    }

    private Policy findIpCheckPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && "ip_check".equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null);
    }

    private Policy findEdgeLimitingPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && "edge_limiting".equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null);
    }

    private Policy findTokenIntrospectionPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && "token_introspection".equalsIgnoreCase(p.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Build AuthPolicy with oauth2Introspection when introspection_url is present.
     * Returns null when required URL is missing (incomplete — caller warns, no full support).
     */
    private String generateOauth2IntrospectionAuthPolicy(String name, String namespace,
                                                          Policy policy, String authCacheBlock) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String endpoint = firstNonBlank(
                cfg.get("introspection_url"),
                cfg.get("introspectionEndpoint"),
                cfg.get("endpoint"));
        if (endpoint == null) {
            return null;
        }

        String tokenTypeHint = firstNonBlank(
                cfg.get("token_type_hint"),
                cfg.get("tokenTypeHint"));
        String hintBlock = tokenTypeHint != null
                ? "          tokenTypeHint: " + tokenTypeHint + "\n"
                : "";

        String secretName = name + "-oauth2-introspection";
        return """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/auth-type: "token-introspection"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      oauth2-introspection:
        oauth2Introspection:
          endpoint: %s
%s          credentialsRef:
            name: %s
%s        credentials:
          authorizationHeader:
            prefix: Bearer
""".formatted(name, namespace, name, name, endpoint, hintBlock, secretName, authCacheBlock);
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Emit RateLimitPolicy from edge_limiting config ∪ application-plan ceilings.
     * Returns null when neither source yields rates (no placeholder file).
     */
    String generateRateLimitPolicy(String name, String namespace, ApiService service) {
        Map<String, String> limitBlocks = new LinkedHashMap<>();

        Policy edge = findEdgeLimitingPolicy(service);
        if (edge != null && edge.configuration != null) {
            appendEdgeLimitingRates(limitBlocks, edge.configuration);
        }

        PlanCeiling ceiling = resolvePlanCeiling(service);
        if (ceiling != null) {
            limitBlocks.put("global", """
      # WARNING: plan ceiling is the max limit across all application plans (not per-plan)
      rates:
        - limit: %d
          window: %s
""".formatted(ceiling.limit, ceiling.window));
        }

        if (limitBlocks.isEmpty()) {
            return null;
        }

        StringBuilder limitsYaml = new StringBuilder();
        for (Map.Entry<String, String> entry : limitBlocks.entrySet()) {
            limitsYaml.append("    ").append(entry.getKey()).append(":\n");
            for (String line : entry.getValue().split("\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                limitsYaml.append(line).append('\n');
            }
        }

        return """
apiVersion: kuadrant.io/v1
kind: RateLimitPolicy
metadata:
  name: %s-ratelimit
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  limits:
%s""".formatted(name, namespace, name, name, limitsYaml);
    }

    @SuppressWarnings("unchecked")
    private void appendEdgeLimitingRates(Map<String, String> limitBlocks, Map<String, Object> cfg) {
        int idx = 1;
        Object fixed = cfg.get("fixed_window_limiters");
        if (fixed instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer count = toPositiveInt(limiter.get("count"));
                Integer window = toPositiveInt(limiter.get("window"));
                if (count == null || window == null) {
                    continue;
                }
                String limitName = edgeLimiterName(limiter, "edge_fixed_window", idx++);
                limitBlocks.put(limitName, """
      rates:
        - limit: %d
          window: %ds
""".formatted(count, window));
            }
        }

        Object leaky = cfg.get("leaky_bucket_limiters");
        if (leaky instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer rate = toPositiveInt(limiter.get("rate"));
                if (rate == null) {
                    continue;
                }
                String limitName = edgeLimiterName(limiter, "edge_leaky_bucket", idx++);
                limitBlocks.put(limitName, """
      # WARNING: 3scale leaky_bucket_limiters approximated as fixed window (window: 1s); not true leaky-bucket semantics
      rates:
        - limit: %d
          window: 1s
""".formatted(rate));
            }
        }

        Object conn = cfg.get("connection_limiters");
        if (conn instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> limiter = (Map<String, Object>) raw;
                Integer connLimit = toPositiveInt(limiter.get("conn"));
                if (connLimit == null) {
                    continue;
                }
                // Best-effort: map concurrent connections to a per-second rate ceiling.
                String limitName = edgeLimiterName(limiter, "edge_conn", idx++);
                limitBlocks.put(limitName, """
      # WARNING: 3scale connection_limiters mapped to a per-second rate ceiling; concurrent-connection semantics are not preserved
      rates:
        - limit: %d
          window: 1s
""".formatted(connLimit));
            }
        }
    }

    private static String edgeLimiterName(Map<String, Object> limiter, String prefix, int idx) {
        Object keyObj = limiter.get("key");
        if (keyObj instanceof Map<?, ?> keyMap) {
            Object name = keyMap.get("name");
            if (name != null) {
                String sanitized = String.valueOf(name)
                        .replaceAll("[^a-zA-Z0-9_-]", "_")
                        .replaceAll("^_+|_+$", "");
                if (!sanitized.isBlank() && !sanitized.contains("{{")) {
                    return sanitized + "_" + idx;
                }
            }
        }
        return prefix + "_" + idx;
    }

    private static Integer toPositiveInt(Object value) {
        if (value instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        if (value != null) {
            try {
                int v = Integer.parseInt(String.valueOf(value).trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Highest plan minute ceiling, else highest hour ceiling (GateForge #202).
     * Never falls back to hardcoded 100/60s when plan data exists.
     */
    private PlanCeiling resolvePlanCeiling(ApiService service) {
        if (service.applicationPlans == null || service.applicationPlans.isEmpty()) {
            return null;
        }
        Integer maxMinute = null;
        Integer maxHour = null;
        for (ApplicationPlan plan : service.applicationPlans) {
            if (plan == null || plan.limits == null) {
                continue;
            }
            for (Map<String, Object> limit : plan.limits) {
                if (limit == null) {
                    continue;
                }
                Object periodObj = limit.get("period");
                Integer value = toPositiveInt(limit.get("value"));
                if (periodObj == null || value == null) {
                    continue;
                }
                String period = String.valueOf(periodObj).toLowerCase();
                if ("minute".equals(period)) {
                    maxMinute = maxMinute == null ? value : Math.max(maxMinute, value);
                } else if ("hour".equals(period)) {
                    maxHour = maxHour == null ? value : Math.max(maxHour, value);
                }
            }
        }
        if (maxMinute != null) {
            return new PlanCeiling(maxMinute, "60s");
        }
        if (maxHour != null) {
            return new PlanCeiling(maxHour, "3600s");
        }
        return null;
    }

    private record PlanCeiling(int limit, String window) {}

    private String generateAuthorizationPolicy(String name, String namespace, Policy ipCheck) {
        Map<String, Object> cfg = ipCheck.configuration != null ? ipCheck.configuration : Map.of();
        String checkType = String.valueOf(cfg.getOrDefault("check_type", "whitelist"));
        List<String> ips = toStringList(cfg.get("ips"));
        boolean deny = "blacklist".equalsIgnoreCase(checkType) || "deny".equalsIgnoreCase(checkType);
        String action = deny ? "DENY" : "ALLOW";

        StringBuilder remoteIps = new StringBuilder();
        for (String ip : ips) {
            String cidr = normalizeCidr(ip);
            if (cidr == null) {
                continue;
            }
            remoteIps.append("        - \"").append(cidr).append("\"\n");
        }
        if (remoteIps.length() == 0) {
            remoteIps.append("        - \"0.0.0.0/0\"\n");
        }

        return """
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: %s-ip-check
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/ip-check-type: "%s"
spec:
  action: %s
  rules:
    - from:
        - source:
            remoteIpBlocks:
%s""".formatted(name, namespace, name, checkType, action, remoteIps);
    }

    /**
     * Normalize a host or CIDR string. Returns {@code null} for null/blank input
     * (callers must skip); never maps blank entries to {@code 0.0.0.0/0}.
     */
    private static String normalizeCidr(String ip) {
        if (ip == null || ip.isBlank()) {
            LOG.warn("Skipping blank/null CIDR entry in IP check policy");
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.contains("/")) {
            return trimmed;
        }
        // Single host → /32
        if (trimmed.contains(":")) {
            return trimmed + "/128";
        }
        return trimmed + "/32";
    }

    private Policy findLoggingPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && "logging".equals(p.name))
                .findFirst()
                .orElse(null);
    }

    private Policy findAuthCachingPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && "3scale_auth_caching".equals(p.name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Convert the 3scale Auth Caching policy (caching_type: allow / strict / resilient)
     * to a per-authentication-rule cache in a Kuadrant AuthPolicy (Authorino cache).
     * cache.key uses the credentials themselves (Authorization header) as the key,
     * reusing authentication results for requests with the same credentials.
     * 3scale's caching_type has detailed semantics such as fail-open/fail-closed,
     * but Authorino only supports simple TTL-based caching, so we do a
     * best-effort mapping from caching_type to an approximate TTL.
     */
    private String buildAuthCacheBlock(Policy authCachingPolicy) {
        if (authCachingPolicy == null) {
            return "";
        }
        String cachingType = authCachingPolicy.configuration != null
                ? String.valueOf(authCachingPolicy.configuration.getOrDefault("caching_type", "strict"))
                : "strict";
        int ttl = switch (cachingType) {
            case "allow" -> 300;
            case "resilient" -> 600;
            default -> 60;
        };
        return """
        cache:
          key:
            selector: request.headers.authorization
          ttl: %d
""".formatted(ttl);
    }

    private Policy findUrlRewritingPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && "url_rewriting".equals(p.name))
                .findFirst()
                .orElse(null);
    }

    // ─────────────────────────────────────────────
    // URL Rewriting (path) → EnvoyFilter (Lua)
    // ─────────────────────────────────────────────

    /**
     * The 3scale URL Rewriting policy (op: sub/gsub, regex, replace) cannot be expressed
     * in Gateway API HTTPRoute (which only supports static ReplaceFullPath / ReplacePrefixMatch),
     * so an Istio EnvoyFilter is used to inject an envoy.filters.http.lua filter
     * that rewrites the request path.
     *
     * 3scale regex/replace uses PCRE + ngx.re.sub syntax (\d, capture references $1).
     * The Envoy Lua filter can only use Lua's standard string.gsub (Lua patterns), so
     * commonly used notations are converted on a best-effort basis (\d → %d, \w → %w, $1/\1 → %1, etc.).
     * Complex PCRE constructs (lookahead, etc.) cannot be converted, so the generated
     * Lua patterns must be manually verified.
     */
    private String generateUrlRewritingEnvoyFilter(String name, String namespace,
            List<Map<String, Object>> commands) {
        StringBuilder rules = new StringBuilder();
        for (Map<String, Object> cmd : commands) {
            String op = String.valueOf(cmd.getOrDefault("op", "sub"));
            String regex = String.valueOf(cmd.getOrDefault("regex", ""));
            String replace = String.valueOf(cmd.getOrDefault("replace", ""));
            if (regex.isBlank()) {
                continue;
            }
            String luaPattern = pcreToLuaPattern(regex);
            String luaReplace = pcreReplaceToLua(replace);
            boolean global = "gsub".equals(op);
            rules.append(String.format(
                "  path = string.gsub(path, \"%s\", \"%s\"%s)%n",
                luaPattern, luaReplace, global ? "" : ", 1"));
        }

        String luaScript = """
function envoy_on_request(request_handle)
  local path = request_handle:headers():get(":path")
  if path == nil then
    return
  end
%s  request_handle:headers():replace(":path", path)
end
""".formatted(rules);

        // Adjust indentation (to match the YAML inlineCode block)
        String indentedScript = luaScript.lines()
                .map(l -> "              " + l)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-url-rewriting
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: url_rewriting
    3scale-migration/note: "Auto-converted from PCRE to Lua patterns on a best-effort basis — verify before use"
spec:
  workloadSelector:
    labels:
      gateway.networking.k8s.io/gateway-name: %s-gateway
  configPatches:
    - applyTo: HTTP_FILTER
      match:
        context: GATEWAY
        listener:
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
              subFilter:
                name: "envoy.filters.http.router"
      patch:
        operation: INSERT_BEFORE
        value:
          name: envoy.filters.http.lua
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua
            inlineCode: |
%s
""".formatted(name, namespace, name, name, indentedScript);
    }

    /** Best-effort conversion of common PCRE notations to Lua patterns. */
    private String pcreToLuaPattern(String pcre) {
        return pcre
                .replace("\\d", "%d")
                .replace("\\w", "%w")
                .replace("\\s", "%s")
                .replace("\\.", "%.");
    }

    /** Convert 3scale replacement strings ($1 / \\1) to Lua's %1 format. */
    private String pcreReplaceToLua(String replace) {
        return replace
                .replaceAll("\\$(\\d)", "%$1")
                .replaceAll("\\\\(\\d)", "%$1");
    }

    @SuppressWarnings("unchecked")
    /**
     * Map 3scale nginx variables to Envoy access log variables.
     * Handles values containing multiple variables (e.g., "uri$request_uri").
     */
    private static String toEnvoyVar(String nginxValue) {
        return nginxValue
                .replace("$request_method",  "%REQ(:METHOD)%")
                .replace("$request_uri",     "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%%QUERY_STRING%")
                .replace("$uri",             "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%")
                .replace("$status",          "%RESPONSE_CODE%")
                .replace("$remote_addr",     "%DOWNSTREAM_REMOTE_ADDRESS_WITHOUT_PORT%")
                .replace("$bytes_sent",      "%BYTES_SENT%")
                .replace("$request_time",    "%DURATION%")
                .replace("$http_user_agent", "%REQ(USER-AGENT)%")
                .replace("$http_referer",    "%REQ(REFERER)%");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonObjectConfig(Object raw) {
        if (raw instanceof List) {
            return (List<Map<String, Object>>) raw;
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JavaType type = om.getTypeFactory()
                        .constructCollectionType(List.class,
                                om.getTypeFactory().constructMapType(
                                        LinkedHashMap.class, String.class, Object.class));
                return om.readValue(str, type);
            } catch (Exception e) {
                LOG.warnf("Failed to parse json_object_config string: %s", e.getMessage());
            }
        }
        return List.of();
    }

    private String generateTelemetry(String name, String namespace, Policy policy, boolean isGateway) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        boolean enableJson = Boolean.TRUE.equals(cfg.get("enable_json_logs"));
        boolean enableAccess = !Boolean.FALSE.equals(cfg.get("enable_access_logs"));
        String selectorLabel = isGateway
                ? "gateway.networking.k8s.io/gateway-name: " + name + "-gateway"
                : "app: " + name;

        return """
apiVersion: telemetry.istio.io/v1
kind: Telemetry
metadata:
  name: %s-logging
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: logging
    3scale-migration/enable-json: "%s"
    3scale-migration/enable-access: "%s"
spec:
  selector:
    matchLabels:
      %s
  accessLogging:
    - providers:
        - name: envoy
""".formatted(name, namespace, name,
                enableJson, enableAccess,
                selectorLabel);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private String generateLoggingEnvoyFilter(String name, String namespace,
            List<Map<String, Object>> jsonCfg, boolean isGateway) {
        StringBuilder jsonFormat = new StringBuilder();
        for (Map<String, Object> entry : jsonCfg) {
            String key        = String.valueOf(entry.getOrDefault("key", ""));
            String value      = String.valueOf(entry.getOrDefault("value", ""));
            String envoyValue = toEnvoyVar(value);
            jsonFormat.append(String.format("                      %s: \"%s\"%n", key, envoyValue));
        }

        String context = isGateway ? "GATEWAY" : "SIDECAR_INBOUND";
        String selectorLabel = isGateway
                ? "gateway.networking.k8s.io/gateway-name: " + name + "-gateway"
                : "app: " + name;

        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-logging-format
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: logging
spec:
  workloadSelector:
    labels:
      %s
  configPatches:
    - applyTo: NETWORK_FILTER
      match:
        context: %s
        listener:
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
      patch:
        operation: MERGE
        value:
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
            access_log:
              - name: envoy.access_loggers.stdout
                typed_config:
                  "@type": type.googleapis.com/envoy.extensions.access_loggers.stream.v3.StdoutAccessLog
                  log_format:
                    json_format:
%s""".formatted(name, namespace, name, selectorLabel, context, jsonFormat.toString());
    }

    private Policy findAnonymousPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && ("default_credentials".equals(p.name) || "anonymous_access".equals(p.name)))
                .findFirst()
                .orElse(null);
    }

    private String generateAnonymousAuthPolicy(String name, String namespace, Policy policy,
            String anonymousTarget) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String authType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));

        // Build response headers using plain.value (secretKeyRef is not in AuthPolicy schema)
        StringBuilder responseHeaders = new StringBuilder();
        if ("user_key".equals(authType)) {
            String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            responseHeaders.append(String.format(
                "          x-user-key:%n            plain:%n              value: \"%s\"%n", userKey));
        } else if ("app_id_and_app_key".equals(authType) || "app_id".equals(authType)) {
            String appId  = String.valueOf(cfg.getOrDefault("app_id",  ConversionConstants.CREDENTIAL_PLACEHOLDER));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            responseHeaders.append(String.format(
                "          x-app-id:%n            plain:%n              value: \"%s\"%n", appId));
            responseHeaders.append(String.format(
                "          x-app-key:%n            plain:%n              value: \"%s\"%n", appKey));
        }

        String responseSection = responseHeaders.length() > 0
                ? "    response:\n      success:\n        headers:\n" + responseHeaders
                : "";

        boolean targetGateway = "gateway".equals(anonymousTarget);
        String targetKind = targetGateway ? "Gateway" : "HTTPRoute";
        String targetName = targetGateway ? name + "-gateway" : name + "-route";

        return """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/anonymous-access: "true"
    3scale-migration/auth-type: "%s"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: %s
    name: %s
  rules:
    authentication:
      anonymous:
        anonymous: {}
%s""".formatted(name, namespace, name, authType, targetKind, targetName, responseSection);
    }

    // ─────────────────────────────────────────────
    // Secret / ConfigMap
    // ─────────────────────────────────────────────

    private static String generateRandomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String generateSecret(String name, String namespace, ApiService service) {
        String authType = service.authentication != null ? service.authentication.type : "none";

        // Anonymous Access: store credentials migrated from 3scale default_credentials policy
        Policy anonymousPolicy = findAnonymousPolicy(service);
        if (anonymousPolicy != null) {
            Map<String, Object> cfg = anonymousPolicy.configuration != null
                    ? anonymousPolicy.configuration : Map.of();
            String polAuthType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));
            StringBuilder stringData = new StringBuilder();
            if ("user_key".equals(polAuthType)) {
                String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
                stringData.append(String.format("  user_key: \"%s\"%n", userKey));
            } else {
                String appId  = String.valueOf(cfg.getOrDefault("app_id",  ConversionConstants.CREDENTIAL_PLACEHOLDER));
                String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
                stringData.append(String.format("  app_id: \"%s\"%n", appId));
                stringData.append(String.format("  app_key: \"%s\"%n", appKey));
            }
            return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-anonymous-credentials
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
type: Opaque
stringData:
%s""".formatted(name, namespace, name, stringData);
        }

        Policy tokenIntrospection = findTokenIntrospectionPolicy(service);
        if (tokenIntrospection != null) {
            Map<String, Object> cfg = tokenIntrospection.configuration != null
                    ? tokenIntrospection.configuration : Map.of();
            String endpoint = firstNonBlank(
                    cfg.get("introspection_url"),
                    cfg.get("introspectionEndpoint"),
                    cfg.get("endpoint"));
            // Same URL gate as AuthPolicy: incomplete introspection must not emit
            // a mismatched oauth2-introspection Secret while policy falls through.
            if (endpoint != null) {
                return generateTokenIntrospectionSecret(name, namespace, tokenIntrospection);
            }
        }

        if ("appIdKey".equals(authType)) {
            return generateAppIdKeySecret(name, namespace, service);
        }

        if ("apiKey".equals(authType)) {
            String apiKey = generateRandomHex(32);
            return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-api-key
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
type: Opaque
stringData:
  api_key: "%s"
""".formatted(name, namespace, name, apiKey);
        }

        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-credentials
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
type: Opaque
stringData:
  client-id: "%s"
  client-secret: "%s"
""".formatted(name, namespace, name,
                ConversionConstants.CREDENTIAL_PLACEHOLDER,
                ConversionConstants.CREDENTIAL_PLACEHOLDER);
    }

    /**
     * Secret for Authorino oauth2Introspection credentialsRef (clientID / clientSecret).
     * Caller must only invoke when introspection_url is present (same gate as AuthPolicy).
     * Missing client credentials emit a WARNING with REPLACE_ME placeholders.
     */
    private String generateTokenIntrospectionSecret(String name, String namespace, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String clientId = firstNonBlank(cfg.get("client_id"), cfg.get("clientID"));
        String clientSecret = firstNonBlank(cfg.get("client_secret"), cfg.get("clientSecret"));

        String warning = "";
        if (clientId == null || clientSecret == null) {
            warning = "# WARNING: token_introspection credentials incomplete — "
                    + "fill clientID/clientSecret before apply\n";
        }

        String idValue = clientId != null ? clientId : ConversionConstants.CREDENTIAL_PLACEHOLDER;
        String secretValue = clientSecret != null ? clientSecret : ConversionConstants.CREDENTIAL_PLACEHOLDER;

        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-oauth2-introspection
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
    auth-type: oauth2-introspection
type: Opaque
%sstringData:
  clientID: "%s"
  clientSecret: "%s"
""".formatted(name, namespace, name, warning, idValue, secretValue);
    }

    /**
     * One Secret with real App ID / App Key pairs as app_id_N / app_key_N.
     * Never invents keys; warns when credentials are missing.
     */
    private String generateAppIdKeySecret(String name, String namespace, ApiService service) {
        List<Application> apps = service.applications != null ? service.applications : List.of();
        StringBuilder stringData = new StringBuilder();
        String warning;
        int index = 1;
        int pairs = 0;
        for (Application app : apps) {
            String appId = app.appId != null && !app.appId.isBlank() ? app.appId : null;
            String appKey = null;
            if (app.keys != null) {
                for (String k : app.keys) {
                    if (k != null && !k.isBlank()) {
                        appKey = k;
                        break;
                    }
                }
            }
            if (appId == null && appKey == null) {
                continue;
            }
            if (appId != null) {
                stringData.append(String.format("  app_id_%d: \"%s\"%n", index, appId));
            }
            if (appKey != null) {
                stringData.append(String.format("  app_key_%d: \"%s\"%n", index, appKey));
                pairs++;
            } else {
                LOG.warnf("App ID %s for service %s has no application keys from Admin API",
                        appId, service.id);
            }
            index++;
        }

        if (pairs == 0 && stringData.length() == 0) {
            warning = "# WARNING: No App ID/App Key credentials fetched from 3scale Admin API — "
                    + "Secret left empty; do not invent keys\n";
            LOG.warnf("No App ID/App Key credentials for service %s; emitting empty Secret with warning",
                    service.id);
            return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-app-id-keys
  namespace: %s
  labels:
    app: %s
    auth-type: app-id-key
    migrated-from: 3scale
    authorino.kuadrant.io/managed-by: authorino
type: Opaque
%sstringData: {}
""".formatted(name, namespace, name, warning);
        } else if (pairs == 0) {
            warning = "# WARNING: App IDs present but application keys missing from Admin API\n";
        } else {
            warning = "";
        }

        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-app-id-keys
  namespace: %s
  labels:
    app: %s
    auth-type: app-id-key
    migrated-from: 3scale
    authorino.kuadrant.io/managed-by: authorino
type: Opaque
%sstringData:
%s""".formatted(name, namespace, name, warning, stringData);
    }

    // ─────────────────────────────────────────────
    // Kuadrant Developer Portal Resources
    // ─────────────────────────────────────────────

    private String generateApiProduct(String name, String namespace, ApiService service) {
        String displayName = service.name != null ? service.name : name;
        String description = service.description != null ? service.description : "Migrated from 3scale";
        return """
apiVersion: devportal.kuadrant.io/v1alpha1
kind: APIProduct
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  displayName: "%s"
  description: "%s"
  approvalMode: automatic
  publishStatus: Published
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  version: v1
""".formatted(name, namespace, name, displayName, description.replace("\"", "'"), name);
    }

    private String generateApiKey(String name, String namespace) {
        return """
apiVersion: devportal.kuadrant.io/v1alpha1
kind: APIKey
metadata:
  name: %s-api-key
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  apiProductRef:
    name: %s
  planTier: basic
  requestedBy:
    email: admin@example.com
    userId: admin
  secretRef:
    name: %s-api-key
""".formatted(name, namespace, name, name, name);
    }

    private String generateConfigMap(String name, String namespace, ApiService service, String backendUrl) {
        String url = "";
        if (backendUrl != null && !backendUrl.isBlank()) {
            url = backendUrl.trim();
        } else if (service.backends != null && !service.backends.isEmpty()) {
            url = service.backends.get(0).privateEndpoint != null
                    ? service.backends.get(0).privateEndpoint : "";
        }
        return """
apiVersion: v1
kind: ConfigMap
metadata:
  name: %s-config
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
data:
  backend-url: "%s"
  service-name: "%s"
  original-3scale-service-id: "%s"
""".formatted(name, namespace, name, url, service.name, service.id);
    }

    // ─────────────────────────────────────────────
    // README
    // ─────────────────────────────────────────────

    private String generateReadme(ApiService service, String name, String namespace,
                                  BackendType backendType, String externalHost) {
        String backendSection = switch (backendType) {
            case EXTERNAL -> """

## External Backend (External HTTPS Service)

The backend is an HTTPS endpoint outside the cluster.

**External endpoint:** `%s`

| File | Description |
|------|-------------|
| serviceentry.yaml | Register the external host with Istio (ServiceEntry + ExternalName Service) |
| destinationrule.yaml | Apply TLS (SIMPLE) for connections to the external host |
| httproute.yaml | Rewrite the Host header to the external hostname via `URLRewrite` |
""".formatted(externalHost);
            case INTERNAL -> """

## Internal Backend (Service within OpenShift)

The backend is a Kubernetes Service within the cluster.
ServiceEntry, DestinationRule, and URLRewrite filters are not needed and have not been generated.

> Verify that `backendRefs.name` in `httproute.yaml` matches the actual Service name.
""";
        };

        boolean hasLogging = findLoggingPolicy(service) != null;
        String loggingFile = hasLogging
                ? "| gateway.yaml | Gateway + Istio Telemetry / EnvoyFilter (access log configuration) |\n" : "";

        boolean hasUrlRewriting = findUrlRewritingPolicy(service) != null;
        String urlRewritingFile = hasUrlRewriting
                ? "| envoyfilter-url-rewriting.yaml | Reproduces the 3scale URL Rewriting policy via Lua filter (PCRE→Lua pattern conversion is best-effort — verify before use) |\n"
                : "";

        String fileList = loggingFile
                + urlRewritingFile
                + (backendType == BackendType.EXTERNAL
                    ? "| serviceentry.yaml | Istio ServiceEntry + ExternalName Service for external backend |\n"
                    + "| destinationrule.yaml | TLS origination to external host |"
                    : "");

        Policy tokenIntrospection = findTokenIntrospectionPolicy(service);
        String tokenIntrospectionNotes = "";
        if (tokenIntrospection != null) {
            Map<String, Object> cfg = tokenIntrospection.configuration != null
                    ? tokenIntrospection.configuration : Map.of();
            String endpoint = firstNonBlank(
                    cfg.get("introspection_url"),
                    cfg.get("introspectionEndpoint"),
                    cfg.get("endpoint"));
            if (endpoint == null) {
                tokenIntrospectionNotes = """

## WARNING: Incomplete token_introspection

The 3scale `token_introspection` policy is present but missing `introspection_url`.
AuthPolicy oauth2Introspection was **not** fully generated — do not claim full support until the
introspection endpoint and client credentials are configured.
""";
            } else {
                tokenIntrospectionNotes = """

## OAuth 2.0 Token Introspection

`policy.yaml` uses AuthPolicy `oauth2Introspection` (endpoint + credentialsRef).
Confirm `secret.yaml` (`%s-oauth2-introspection`) clientID/clientSecret before apply.
""".formatted(name);
            }
        }

        String rateLimitNotes = buildRateLimitApproximationNotes(service);
        String jwtClaimCheckNotes = buildJwtClaimCheckReadmeNotes(service);

        return """
# %s - Connectivity Link Migration

## Overview
Kubernetes/OpenShift resources generated by Migration Toolkit.

**Original 3scale service:** %s (ID: %s)
**Target Namespace:** %s
**Backend type:** %s
%s
## Files

| File | Description |
|------|-------------|
| gateway.yaml | Gateway serving as the entry point for external traffic |
| httproute.yaml | HTTPRoute converted from 3scale mapping rules |
| policy.yaml | Authentication/authorization policy (AuthPolicy) |
| secret.yaml | Credentials (replace values before applying) |
| configmap.yaml | Configuration data |
%s
%s%s%s
## Prerequisites
- OpenShift with Connectivity Link (Kuadrant) operator
- Gateway API CRDs
- Istio

## Installation

```bash
# Review and update the values in secret.yaml before applying
vi secret.yaml
kubectl apply -f . -n %s

# Verify Gateway
kubectl get gateway %s-gateway -n %s
kubectl get httproute %s-route -n %s
```

## Notes
- Make sure to update the credentials in `secret.yaml` before applying
- Verify that the backend service name in `httproute.yaml` matches the actual Service name
- Test in a staging environment first
""".formatted(
            service.name, service.name, service.id, namespace,
            backendType == BackendType.EXTERNAL ? "External HTTPS" : "Internal OpenShift Service",
            backendSection,
            fileList,
            tokenIntrospectionNotes,
            rateLimitNotes,
            jwtClaimCheckNotes,
            namespace, name, namespace, name, namespace
        );
    }

    /**
     * Operator-facing WARNINGs for rate-limit semantic approximations (#8–#10).
     * Empty when neither edge_limiting approximations nor plan ceilings apply.
     */
    private String buildRateLimitApproximationNotes(ApiService service) {
        boolean hasLeaky = false;
        boolean hasConn = false;
        Policy edge = findEdgeLimitingPolicy(service);
        if (edge != null && edge.configuration != null) {
            Object leaky = edge.configuration.get("leaky_bucket_limiters");
            if (leaky instanceof List<?> list && !list.isEmpty()) {
                hasLeaky = true;
            }
            Object conn = edge.configuration.get("connection_limiters");
            if (conn instanceof List<?> list && !list.isEmpty()) {
                hasConn = true;
            }
        }
        boolean hasPlanCeiling = resolvePlanCeiling(service) != null;
        if (!hasLeaky && !hasConn && !hasPlanCeiling) {
            return "";
        }

        StringBuilder bullets = new StringBuilder();
        if (hasConn) {
            bullets.append(
                    "- **connection_limiters → rate**: concurrent connections are approximated as a "
                            + "per-second rate ceiling (`window: 1s`); connection semantics are not preserved\n");
        }
        if (hasLeaky) {
            bullets.append(
                    "- **leaky_bucket → fixed window**: leaky-bucket limiters are emitted as fixed "
                            + "`window: 1s` rates; not true leaky-bucket semantics\n");
        }
        if (hasPlanCeiling) {
            bullets.append(
                    "- **plan ceiling**: `global` limit is the **max** across all application plans "
                            + "(not a per-plan ceiling)\n");
        }
        return """

## WARNING: Rate-limit approximations

`ratelimitpolicy.yaml` includes best-effort mappings from 3scale. Review before apply:

%s""".formatted(bullets);
    }

    private String buildJwtClaimCheckReadmeNotes(ApiService service) {
        Policy claimCheck = findJwtClaimCheckPolicy(service);
        if (claimCheck == null) {
            return "";
        }
        JwtClaimParseResult parsed = parseJwtClaimCheckRules(claimCheck);
        if (parsed.gapNotes().isEmpty() && parsed.patterns().isEmpty()) {
            return "";
        }
        if (parsed.gapNotes().isEmpty()) {
            return """

## JWT Claim Check

`policy.yaml` includes AuthPolicy `authorization.jwt-claim-check` patternMatching rules
mapped from 3scale `jwt_claim_check` (`==`→`eq`, `!=`→`neq`, `matches`→`matches` on `auth.identity.*`).
""";
        }
        StringBuilder bullets = new StringBuilder();
        for (String note : parsed.gapNotes().stream().distinct().toList()) {
            bullets.append("- ").append(note).append('\n');
        }
        return """

## WARNING: JWT Claim Check conversion gaps

3scale `jwt_claim_check` was partially converted to AuthPolicy `patternMatching`.
Review the following limitations before apply:

%s
Liquid claim/value templates, `combine_op=or`, and path/method-gated deny semantics are not fully
reproduced — verify authorization behavior against the original APIcast policy.
""".formatted(bullets);
    }

    // ─────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────

    private String toKebabCase(String input) {
        if (input == null) {
            return "service";
        }
        return input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
