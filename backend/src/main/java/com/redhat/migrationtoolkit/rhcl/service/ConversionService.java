package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class ConversionService {

    /**
     * バックエンドの種別を表す列挙型。
     * INTERNAL : OpenShift/Kubernetes 内の Service（ServiceEntry・DestinationRule・URLRewrite 不要）
     * EXTERNAL : クラスター外の HTTPS エンドポイント（ServiceEntry・DestinationRule・URLRewrite が必要）
     */
    enum BackendType { INTERNAL, EXTERNAL }

    public Map<String, String> convert(ApiService service, String namespace) {
        return convert(service, namespace, null);
    }

    public Map<String, String> convert(ApiService service, String namespace, String backendUrl) {
        Map<String, String> files = new LinkedHashMap<>();
        String name = toKebabCase(service.systemName != null ? service.systemName : service.name);

        BackendType backendType = detectBackendType(backendUrl);
        String externalHost = backendType == BackendType.EXTERNAL ? extractHostname(backendUrl) : null;
        String internalService = backendType == BackendType.INTERNAL
                ? extractInternalService(backendUrl, name) : null;
        int internalPort = backendType == BackendType.INTERNAL ? extractPort(backendUrl, 8080) : 8080;

        files.put("gateway.yaml",    generateGateway(name, namespace));
        files.put("httproute.yaml",  generateHttpRoute(
                name, namespace, service, backendType, externalHost, internalService, internalPort));
        files.put("policy.yaml",     generateAuthPolicy(name, namespace, service));
        files.put("secret.yaml",     generateSecret(name, namespace, service));
        files.put("configmap.yaml",  generateConfigMap(name, namespace, service, backendUrl));
        files.put("apiproduct.yaml", generateApiProduct(name, namespace, service));

        String authType = service.authentication != null ? service.authentication.type : "none";
        if ("apiKey".equals(authType)) {
            files.put("apikey.yaml", generateApiKey(name, namespace));
        }

        if (backendType == BackendType.EXTERNAL) {
            files.put("serviceentry.yaml",    generateServiceEntry(name, namespace, externalHost));
            files.put("destinationrule.yaml", generateDestinationRule(name, namespace, externalHost));
        }

        Policy loggingPolicy = findLoggingPolicy(service);
        if (loggingPolicy != null) {
            files.put("telemetry.yaml", generateTelemetry(name, namespace, loggingPolicy));
        }

        files.put("README.md", generateReadme(service, name, namespace, backendType, externalHost));
        return files;
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
                                     String internalService, int internalPort) {
        // 外部バックエンド: port 443 + URLRewrite で Host ヘッダーを書き換え
        // 内部バックエンド: URL 指定のポート（デフォルト 8080）、フィルターなし
        int backendPort   = backendType == BackendType.EXTERNAL ? 443 : internalPort;
        String backendSvc = backendType == BackendType.EXTERNAL
                ? (name + "-backend")
                : (internalService != null ? internalService : name + "-backend");

        String urlRewriteFilter = backendType == BackendType.EXTERNAL ? """
      filters:
        - type: URLRewrite
          urlRewrite:
            hostname: "%s"
""".formatted(externalHost) : "";

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
            for (MappingRule rule : service.mappingRules) {
                String path   = rule.pattern != null ? rule.pattern.replaceAll("\\{\\?\\}", "*") : "/";
                String method = rule.httpMethod != null ? rule.httpMethod : "GET";
                sb.append("""
    - matches:
        - path:
            type: PathPrefix
            value: "%s"
          method: %s
%s%s      backendRefs:
        - name: %s
          port: %d
""".formatted(path, method, urlRewriteFilter, timeoutsBlock, backendSvc, backendPort));
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
""".formatted(urlRewriteFilter, timeoutsBlock, backendSvc, backendPort));
        }
        return sb.toString();
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

    private String generateServiceEntry(String name, String namespace, String externalHost) {
        String backendSvc = name + "-backend";
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
  - number: 443
    name: https
    protocol: HTTPS
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
  - name: https
    port: 443
""".formatted(name, namespace, name, externalHost,
              backendSvc, namespace, name, externalHost);
    }

    // ─────────────────────────────────────────────
    // DestinationRule（外部バックエンドのみ生成）
    // ─────────────────────────────────────────────

    private String generateDestinationRule(String name, String namespace, String externalHost) {
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
  trafficPolicy:
    tls:
      mode: SIMPLE
      sni: %s
""".formatted(name, namespace, name, externalHost, externalHost);
    }

    // ─────────────────────────────────────────────
    // AuthPolicy
    // ─────────────────────────────────────────────

    private String generateAuthPolicy(String name, String namespace, ApiService service) {
        String authType = service.authentication != null ? service.authentication.type : "none";

        // Anonymous Access (default_credentials policy) — inject credentials as response headers
        Policy anonymousPolicy = findAnonymousPolicy(service);
        if (anonymousPolicy != null) {
            return generateAnonymousAuthPolicy(name, namespace, anonymousPolicy);
        }

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
""".formatted(name, namespace, name, name, issuer);
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
        credentials:
          authorizationHeader:
            prefix: APIKEY
""".formatted(name, namespace, name, name, name);
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
                return om.readValue(str, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                LOG.warnf("Failed to parse json_object_config string: %s", e.getMessage());
            }
        }
        return java.util.List.of();
    }

    private String generateTelemetry(String name, String namespace, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        boolean enableJson = Boolean.TRUE.equals(cfg.get("enable_json_logs"));
        boolean enableAccess = !Boolean.FALSE.equals(cfg.get("enable_access_logs"));

        // json_object_config → Istio format.labels（Envoy変数にマッピング）
        // String / List どちらの形式で来ても処理する
        StringBuilder labelsBuilder = new StringBuilder();
        java.util.List<Map<String, Object>> jsonCfg = parseJsonObjectConfig(cfg.get("json_object_config"));
        for (Map<String, Object> entry : jsonCfg) {
            String key        = String.valueOf(entry.getOrDefault("key", ""));
            String value      = String.valueOf(entry.getOrDefault("value", ""));
            String envoyValue = toEnvoyVar(value);
            labelsBuilder.append(String.format("        %s: \"%s\"%n", key, envoyValue));
        }

        String formatSection = labelsBuilder.length() > 0
                ? "      format:\n        labels:\n" + labelsBuilder
                : "";

        // enable_access_logs の値に関わらず、Istio では format.labels を有効にして出力する。
        // 3scale の enable_access_logs: false はAPICastのアクセスログ制御であり、
        // Connectivity Link では Istio Telemetry で独立して管理するため。
        String accessLoggingSection = "  accessLogging:\n"
                + "    - providers:\n"
                + "        - name: envoy\n"
                + formatSection;

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
      app: %s
%s""".formatted(name, namespace, name,
                enableJson, enableAccess,
                name, accessLoggingSection);
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

    private String generateAnonymousAuthPolicy(String name, String namespace, Policy policy) {
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
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      anonymous:
        anonymous: {}
%s""".formatted(name, namespace, name, authType, name, responseSection);
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
                ? "| telemetry.yaml | Istio Telemetry リソース（アクセスログ設定） |\n" : "";

        String fileList = loggingFile
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
