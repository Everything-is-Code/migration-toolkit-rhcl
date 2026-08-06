package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class ConversionService {

    private static final Logger LOG = Logger.getLogger(ConversionService.class);

    /**
     * バックエンドの種別を表す列挙型。
     * INTERNAL : OpenShift/Kubernetes 内の Service（ServiceEntry・DestinationRule・URLRewrite 不要）
     * EXTERNAL : クラスター外の HTTPS エンドポイント（ServiceEntry・DestinationRule・URLRewrite が必要）
     */
    enum BackendType { INTERNAL, EXTERNAL }

    public Map<String, String> convert(ApiService service, String namespace) {
        return convert(service, namespace, null, "gateway", "httproute");
    }

    public Map<String, String> convert(ApiService service, String namespace, String backendUrl) {
        return convert(service, namespace, backendUrl, "gateway", "httproute");
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget) {
        return convert(service, namespace, backendUrl, loggingTarget, "httproute");
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget) {
        return convert(service, namespace, backendUrl, loggingTarget, anonymousTarget, true);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget,
            boolean includeMigratedFromLabel) {
        Map<String, String> files = new LinkedHashMap<>();
        String name = toKebabCase(service.systemName != null ? service.systemName : service.name);

        // ユーザーが明示的に URL を指定しなかった場合は、3scale に登録済みの
        // 実際のバックエンド (privateEndpoint) を使ってタイプを自動判定する。
        // configmap.yaml のフォールバックと一致させることで、
        // 「バックエンド URL は ELB を指しているのに HTTPRoute は
        //  存在しないクラスター内部 Service を指す」という不整合を防ぐ。
        String effectiveBackendUrl = backendUrl;
        if ((effectiveBackendUrl == null || effectiveBackendUrl.isBlank())
                && service.backends != null && !service.backends.isEmpty()) {
            effectiveBackendUrl = service.backends.get(0).privateEndpoint;
        }

        BackendType backendType = detectBackendType(effectiveBackendUrl);
        String externalHost = backendType == BackendType.EXTERNAL ? extractHostname(effectiveBackendUrl) : null;
        String internalService = backendType == BackendType.INTERNAL
                ? extractInternalService(effectiveBackendUrl, name) : null;
        int internalPort = backendType == BackendType.INTERNAL ? extractPort(effectiveBackendUrl, 8080) : 8080;
        // 外部バックエンドのポートは URL のスキーム（http→80 / https→443）または
        // 明示ポートから決定する。443 に固定すると、実際は HTTP のみで待ち受けている
        // バックエンド（TLS 未設定の OpenShift Route など）に接続できなくなる。
        int externalDefaultPort = effectiveBackendUrl != null && effectiveBackendUrl.trim().startsWith("http://")
                ? 80 : 443;
        int externalPort = backendType == BackendType.EXTERNAL
                ? extractPort(effectiveBackendUrl, externalDefaultPort) : 443;

        files.put("gateway.yaml", generateGateway(name, namespace));
        files.put("httproute.yaml",  generateHttpRoute(
                name, namespace, service, backendType, externalHost, internalService, internalPort, externalPort));
        files.put("policy.yaml",     generateAuthPolicy(name, namespace, service, anonymousTarget));
        files.put("secret.yaml",     generateSecret(name, namespace, service));
        files.put("configmap.yaml",  generateConfigMap(name, namespace, service, effectiveBackendUrl));
        files.put("apiproduct.yaml", generateApiProduct(name, namespace, service));

        String authType = service.authentication != null ? service.authentication.type : "none";
        if ("apiKey".equals(authType)) {
            files.put("apikey.yaml", generateApiKey(name, namespace));
        }

        if (backendType == BackendType.EXTERNAL) {
            boolean externalUsesTls = externalPort == 443;
            files.put("serviceentry.yaml",
                    generateServiceEntry(name, namespace, externalHost, externalPort, externalUsesTls));
            files.put("destinationrule.yaml",
                    generateDestinationRule(name, namespace, externalHost, externalUsesTls));
        }

        Policy loggingPolicy = findLoggingPolicy(service);
        if (loggingPolicy != null) {
            boolean isGateway = !"workload".equals(loggingTarget);
            files.put("telemetry.yaml", generateTelemetry(name, namespace, loggingPolicy, isGateway));
            java.util.List<Map<String, Object>> jsonCfgCheck =
                    parseJsonObjectConfig(loggingPolicy.configuration != null
                            ? loggingPolicy.configuration.get("json_object_config") : null);
            if (!jsonCfgCheck.isEmpty()) {
                files.put("envoyfilter-logging.yaml",
                        generateLoggingEnvoyFilter(name, namespace, jsonCfgCheck, isGateway));
            }
        }

        Policy urlRewritingPolicy = findUrlRewritingPolicy(service);
        if (urlRewritingPolicy != null) {
            java.util.List<Map<String, Object>> rewriteCommands = parseJsonObjectConfig(
                    urlRewritingPolicy.configuration != null
                            ? urlRewritingPolicy.configuration.get("commands") : null);
            if (!rewriteCommands.isEmpty()) {
                files.put("envoyfilter-url-rewriting.yaml",
                        generateUrlRewritingEnvoyFilter(name, namespace, rewriteCommands));
            }
        }

        files.put("README.md", generateReadme(service, name, namespace, backendType, externalHost));

        if (!includeMigratedFromLabel) {
            files.replaceAll((fileName, content) -> stripMigratedFromLabel(content));
        }
        return files;
    }

    /** 生成 YAML から "migrated-from: 3scale" ラベル行を取り除く（チェックボックスで無効化された場合）。 */
    private String stripMigratedFromLabel(String content) {
        return content.replaceAll("(?m)^[ \\t]*migrated-from: 3scale\\R?", "");
    }

    // ─────────────────────────────────────────────
    // バックエンドタイプ判定
    // ─────────────────────────────────────────────

    /**
     * バックエンド URL からタイプを判定する。
     *   null / 空文字          → INTERNAL（デフォルト）
     *   *.svc / *.svc.cluster.local 形式 → INTERNAL
     *   クラスター内 DNS（ドット区切りのないホスト名）→ INTERNAL
     *   https?://external...   → EXTERNAL
     */
    BackendType detectBackendType(String url) {
        if (url == null || url.isBlank()) {
            return BackendType.INTERNAL;
        }
        String host = extractHostname(url);
        if (host == null) {
            return BackendType.INTERNAL;
        }
        // *.svc または *.svc.cluster.local → 内部
        if (host.endsWith(".svc") || host.endsWith(".svc.cluster.local")) {
            return BackendType.INTERNAL;
        }
        // ドットを含まないシンプルなホスト名（例: my-service）→ 内部
        if (!host.contains(".")) {
            return BackendType.INTERNAL;
        }
        return BackendType.EXTERNAL;
    }

    /** URL からホスト名を抽出する。失敗時は null。 */
    private String extractHostname(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "https://" + s;
            }
            return new java.net.URI(s).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 内部バックエンド URL からサービス名を抽出する。
     * "http://my-service:8080" → "my-service"
     * 抽出できない場合は "{name}-backend" を返す。
     */
    private String extractInternalService(String url, String name) {
        String host = extractHostname(url);
        if (host == null || host.isBlank()) {
            return name + "-backend";
        }
        // "svc.cluster.local" サフィックスを除去して先頭のサービス名だけ返す
        return host.split("\\.")[0];
    }

    /** URL からポート番号を抽出する。失敗時はデフォルト値を返す。 */
    private int extractPort(String url, int defaultPort) {
        if (url == null || url.isBlank()) {
            return defaultPort;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "http://" + s;
            }
            int port = new java.net.URI(s).getPort();
            return port > 0 ? port : defaultPort;
        } catch (Exception e) {
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
      port: 80
      allowedRoutes:
        namespaces:
          from: Same
    - name: https
      protocol: HTTPS
      port: 443
      tls:
        mode: Terminate
        certificateRefs:
          - name: %s-tls
      allowedRoutes:
        namespaces:
          from: Same
""".formatted(name, namespace, name, name);
    }

    // ─────────────────────────────────────────────
    // HTTPRoute
    // ─────────────────────────────────────────────

    private String generateHttpRoute(String name, String namespace, ApiService service,
                                     BackendType backendType, String externalHost,
                                     String internalService, int internalPort, int externalPort) {
        int backendPort   = backendType == BackendType.EXTERNAL ? externalPort : internalPort;
        String backendSvc = backendType == BackendType.EXTERNAL
                ? (name + "-backend")
                : (internalService != null ? internalService : name + "-backend");

        // filters ブロックを構築（URLRewrite + Header Modification を統合）
        StringBuilder filterItems = new StringBuilder();
        if (backendType == BackendType.EXTERNAL) {
            filterItems.append("""
        - type: URLRewrite
          urlRewrite:
            hostname: "%s"
""".formatted(externalHost));
        }
        filterItems.append(buildHeaderModificationFilters(service));

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

        if (service.mappingRules != null && !service.mappingRules.isEmpty()) {
            // "/" (catch-all) な Mapping Rule が既にある HTTP メソッドについては、
            // それより後ろにある同メソッドのルールは常に "/" に包含され冗長なのでスキップする。
            java.util.Set<String> catchAllMethods = new java.util.HashSet<>();
            java.util.Set<String> emitted = new java.util.LinkedHashSet<>();
            for (MappingRule rule : service.mappingRules) {
                String path   = toGatewayApiPathPrefix(rule.pattern);
                String method = rule.httpMethod != null ? rule.httpMethod : "GET";

                if (catchAllMethods.contains(method) || !emitted.add(path + " " + method)) {
                    continue;
                }
                if ("/".equals(path)) {
                    catchAllMethods.add(method);
                }

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
        return sb.toString();
    }

    /**
     * 3scale の Mapping Rule パターン（例: "/api/dashboard/{id}", "/foo/{?}"）を
     * Gateway API の PathPrefix で使える値に変換する。
     * Gateway API の path.value は `^(?:[-A-Za-z0-9/._~!$&'()*+,;=:@]|[%][0-9a-fA-F]{2})+$`
     * のみ許可され、`{`/`}` を含むテンプレート化されたパスパラメータは指定できない。
     * そのため、最初のパスパラメータの直前までを PathPrefix として使用する
     * （例: "/api/dashboard/{id}" → "/api/dashboard"）。
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
                .filter(p -> Boolean.TRUE.equals(p.enabled) && "headers".equals(p.name))
                .findFirst().orElse(null);
        if (policy == null || policy.configuration == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (String direction : new String[]{"response", "request"}) {
            Object raw = policy.configuration.get(direction);
            if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
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

    /**
     * upstream_connection ポリシーの各タイムアウト値を Gateway API timeouts フィールドに変換する。
     *   connect_timeout → backendRequest（バックエンド接続タイムアウト）
     *   read_timeout    → request（レスポンス受信タイムアウト）
     *   send_timeout    → アノテーションに記録（Gateway API に直接対応フィールドなし）
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
     * upstream_connection の send_timeout をアノテーションとして返す（HTTPRoute metadata に付与）。
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
    // ServiceEntry（外部バックエンドのみ生成）
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
    // DestinationRule（外部バックエンドのみ生成）
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
            String anonymousTarget) {
        String authType = service.authentication != null ? service.authentication.type : "none";

        // Anonymous Access (default_credentials policy) — inject credentials as response headers
        Policy anonymousPolicy = findAnonymousPolicy(service);
        if (anonymousPolicy != null) {
            return generateAnonymousAuthPolicy(name, namespace, anonymousPolicy, anonymousTarget);
        }

        String authCacheBlock = buildAuthCacheBlock(findAuthCachingPolicy(service));

        if ("jwt".equals(authType)) {
            String issuer = service.authentication.oidcIssuerEndpoint != null
                    ? service.authentication.oidcIssuerEndpoint
                    : "https://your-oidc-provider/realms/your-realm";
            return """
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
        } else if ("apiKey".equals(authType)) {
            return """
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
          allNamespaces: true
          selector:
            matchLabels:
              app: %s
%s        credentials:
          authorizationHeader:
            prefix: APIKEY
""".formatted(name, namespace, name, name, name, authCacheBlock);
        }

        return """
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
     * 3scale の Auth Caching ポリシー（caching_type: allow / strict / resilient）を
     * Kuadrant AuthPolicy の認証ルール単位の cache（Authorino のキャッシュ）へ変換する。
     * cache.key は認証情報そのもの（Authorization ヘッダー）をキーにして、
     * 同一クレデンシャルからのリクエストに対する認証結果を再利用する。
     * 3scale の caching_type は fail-open/fail-closed 等の詳細な意味論を持つが、
     * Authorino 側は単純な TTL ベースのキャッシュしか持たないため、
     * caching_type から TTL 目安へベストエフォートでマッピングする。
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
     * 3scale の URL Rewriting ポリシー（op: sub/gsub, regex, replace）を
     * Gateway API の HTTPRoute では表現できない（サポートするのは静的な
     * ReplaceFullPath / ReplacePrefixMatch のみ）ため、Istio EnvoyFilter で
     * envoy.filters.http.lua フィルターを挿入し、リクエストパスを書き換える。
     *
     * 3scale の regex/replace は PCRE + ngx.re.sub 構文（\d, キャプチャ参照 $1）。
     * Envoy Lua フィルターは Lua 標準の string.gsub（Lua パターン）しか使えないため、
     * よく使われる記法をベストエフォートで変換する（\d → %d, \w → %w, $1/\1 → %1 等）。
     * 複雑な PCRE 構文（先読み等）は変換できないため、生成された Lua パターンは
     * 必ず目視確認すること。
     */
    private String generateUrlRewritingEnvoyFilter(String name, String namespace,
            java.util.List<Map<String, Object>> commands) {
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

        // インデント調整（YAML の inlineCode ブロックに合わせる）
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

    /** PCRE の代表的な記法を Lua パターンへベストエフォートで変換する。 */
    private String pcreToLuaPattern(String pcre) {
        return pcre
                .replace("\\d", "%d")
                .replace("\\w", "%w")
                .replace("\\s", "%s")
                .replace("\\.", "%.");
    }

    /** 3scale の置換文字列（$1 / \\1）を Lua の %1 形式へ変換する。 */
    private String pcreReplaceToLua(String replace) {
        return replace
                .replaceAll("\\$(\\d)", "%$1")
                .replaceAll("\\\\(\\d)", "%$1");
    }

    @SuppressWarnings("unchecked")
    /**
     * 3scale の nginx 変数を Envoy アクセスログ変数にマッピングする。
     * 値に複数の変数が混在する場合（例: "uri$request_uri"）も対応。
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
    private java.util.List<Map<String, Object>> parseJsonObjectConfig(Object raw) {
        if (raw instanceof java.util.List) {
            return (java.util.List<Map<String, Object>>) raw;
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JavaType type = om.getTypeFactory()
                        .constructCollectionType(java.util.List.class,
                                om.getTypeFactory().constructMapType(
                                        java.util.LinkedHashMap.class, String.class, Object.class));
                return om.readValue(str, type);
            } catch (Exception e) {
                LOG.warnf("Failed to parse json_object_config string: %s", e.getMessage());
            }
        }
        return java.util.List.of();
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
            java.util.List<Map<String, Object>> jsonCfg, boolean isGateway) {
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
            String userKey = String.valueOf(cfg.getOrDefault("user_key", "REPLACE_ME"));
            responseHeaders.append(String.format(
                "          x-user-key:%n            plain:%n              value: \"%s\"%n", userKey));
        } else if ("app_id_and_app_key".equals(authType) || "app_id".equals(authType)) {
            String appId  = String.valueOf(cfg.getOrDefault("app_id",  "REPLACE_ME"));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", "REPLACE_ME"));
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
                String userKey = String.valueOf(cfg.getOrDefault("user_key", "REPLACE_ME"));
                stringData.append(String.format("  user_key: \"%s\"%n", userKey));
            } else {
                String appId  = String.valueOf(cfg.getOrDefault("app_id",  "REPLACE_ME"));
                String appKey = String.valueOf(cfg.getOrDefault("app_key", "REPLACE_ME"));
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
  client-id: "REPLACE_ME"
  client-secret: "REPLACE_ME"
""".formatted(name, namespace, name);
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

## External Backend（外部 HTTPS サービス）

バックエンドはクラスター外の HTTPS エンドポイントです。

**外部エンドポイント:** `%s`

| File | 説明 |
|------|------|
| serviceentry.yaml | Istio に外部ホストを登録（ServiceEntry + ExternalName Service） |
| destinationrule.yaml | 外部への接続に TLS（SIMPLE）を適用 |
| httproute.yaml | `URLRewrite` で Host ヘッダーを外部ホスト名に書き換え |
""".formatted(externalHost);
            case INTERNAL -> """

## Internal Backend（OpenShift 内 Service）

バックエンドはクラスター内の Kubernetes Service です。
ServiceEntry・DestinationRule・URLRewrite フィルターは不要なため生成されていません。

> `httproute.yaml` の `backendRefs.name` が実際の Service 名と一致していることを確認してください。
""";
        };

        boolean hasLogging = findLoggingPolicy(service) != null;
        String loggingFile = hasLogging
                ? "| gateway.yaml | Gateway + Istio Telemetry / EnvoyFilter（アクセスログ設定） |\n" : "";

        boolean hasUrlRewriting = findUrlRewritingPolicy(service) != null;
        String urlRewritingFile = hasUrlRewriting
                ? "| envoyfilter-url-rewriting.yaml | 3scale URL Rewriting ポリシーを Lua フィルターで再現（PCRE→Lua パターンはベストエフォート変換のため要確認） |\n"
                : "";

        String fileList = loggingFile
                + urlRewritingFile
                + (backendType == BackendType.EXTERNAL
                    ? "| serviceentry.yaml | Istio ServiceEntry + ExternalName Service for external backend |\n"
                    + "| destinationrule.yaml | TLS origination to external host |"
                    : "");

        return """
# %s - Connectivity Link Migration

## Overview
Migration Toolkit で生成した Kubernetes/OpenShift リソースです。

**元の 3scale サービス:** %s (ID: %s)
**対象 Namespace:** %s
**バックエンドタイプ:** %s
%s
## Files

| File | 説明 |
|------|------|
| gateway.yaml | 外部トラフィックの入口となる Gateway |
| httproute.yaml | 3scale マッピングルールから変換した HTTPRoute |
| policy.yaml | 認証・認可ポリシー（AuthPolicy） |
| secret.yaml | 認証情報（apply 前に値を置き換えてください） |
| configmap.yaml | 設定情報 |
%s

## Prerequisites
- OpenShift with Connectivity Link (Kuadrant) operator
- Gateway API CRDs
- Istio

## Installation

```bash
# secret.yaml の値を確認・更新してから適用
vi secret.yaml
kubectl apply -f . -n %s

# Gateway 確認
kubectl get gateway %s-gateway -n %s
kubectl get httproute %s-route -n %s
```

## Notes
- `secret.yaml` の認証情報を apply 前に必ず更新してください
- `httproute.yaml` のバックエンドサービス名が実際の Service 名と一致しているか確認してください
- まずステージング環境でテストしてください
""".formatted(
            service.name, service.name, service.id, namespace,
            backendType == BackendType.EXTERNAL ? "External HTTPS" : "Internal OpenShift Service",
            backendSection,
            fileList,
            namespace, name, namespace, name, namespace
        );
    }

    // ─────────────────────────────────────────────
    // ユーティリティ
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
