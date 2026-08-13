package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversionServiceTest {

    private ConversionService service;

    @BeforeEach
    void setUp() {
        service = new ConversionService();
    }

    // ── convert() basic output ────────────────────────────────────────────────

    @Test
    void convert_basicService_producesRequiredFiles() {
        ApiService svc = basicService("my-api", "my-api");
        Map<String, String> files = service.convert(svc, "test-ns");

        assertTrue(files.containsKey("gateway.yaml"));
        assertTrue(files.containsKey("httproute.yaml"));
        assertTrue(files.containsKey("policy.yaml"));
        assertTrue(files.containsKey("secret.yaml"));
        assertTrue(files.containsKey("configmap.yaml"));
        assertTrue(files.containsKey("apiproduct.yaml"));
        assertTrue(files.containsKey("README.md"));
    }

    @Test
    void convert_apiKeyAuth_includesApiKeyYaml() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("apiKey");
        Map<String, String> files = service.convert(svc, "test-ns");
        assertTrue(files.containsKey("apikey.yaml"));
        assertTrue(files.containsKey("secret.yaml"));
    }

    @Test
    void convert_jwtAuth_noApiKeyYaml() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "test-ns");
        assertFalse(files.containsKey("apikey.yaml"));
    }

    @Test
    void convert_externalBackend_includesServiceEntry() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "test-ns", "https://api.external.example.com");
        assertTrue(files.containsKey("serviceentry.yaml"));
        assertTrue(files.containsKey("destinationrule.yaml"));
    }

    @Test
    void convert_internalBackend_noServiceEntry() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "test-ns", "http://my-service:8080");
        assertFalse(files.containsKey("serviceentry.yaml"));
        assertFalse(files.containsKey("destinationrule.yaml"));
    }

    @Test
    void convert_nullBackendUrl_treatedAsInternal() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "test-ns", null);
        assertFalse(files.containsKey("serviceentry.yaml"));
    }

    // ── Gateway YAML content ──────────────────────────────────────────────────

    @Test
    void convert_gatewayYaml_containsNamespace() {
        ApiService svc = basicService("test-svc", "test-svc");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "my-namespace");
        String gw = files.get("gateway.yaml");
        assertTrue(gw.contains("namespace: my-namespace"));
    }

    @Test
    void convert_gatewayYaml_containsGatewayClass() {
        ApiService svc = basicService("test-svc", "test-svc");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String gw = files.get("gateway.yaml");
        assertTrue(gw.contains("gatewayClassName: istio"));
        assertTrue(gw.contains("apiVersion: gateway.networking.k8s.io/v1"));
        assertTrue(gw.contains("kind: Gateway"));
    }

    // ── HTTPRoute YAML content ────────────────────────────────────────────────

    @Test
    void convert_httpRouteYaml_containsMappingRules() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/users";
        svc.mappingRules = List.of(rule);

        Map<String, String> files = service.convert(svc, "ns");
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("/api/users"));
        assertTrue(httproute.contains("GET"));
    }

    @Test
    void convert_httpRouteYaml_wildcardReplaced() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/{?}";
        svc.mappingRules = List.of(rule);

        Map<String, String> files = service.convert(svc, "ns");
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("*"), "Wildcard {?} should be replaced with *");
        assertFalse(httproute.contains("{?}"));
    }

    @Test
    void convert_externalBackend_httpRouteContainsUrlRewrite() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", "https://api.external.com");
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("URLRewrite") || httproute.contains("urlRewrite"));
    }

    // ── Header modification alias (header_modification ≡ headers) ─────────────

    @Test
    void convert_headersPolicy_emitsResponseHeaderModifier() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(headerPolicy("headers", "X-From-Headers", "headers-value"));

        Map<String, String> files = service.convert(svc, "ns");
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("ResponseHeaderModifier"));
        assertTrue(httproute.contains("X-From-Headers"));
        assertTrue(httproute.contains("headers-value"));
    }

    @Test
    void convert_headerModificationAlias_matchesHeadersOutput() {
        ApiService withHeaders = basicService("my-api", "my-api");
        withHeaders.authentication = auth("jwt");
        withHeaders.policies = List.of(headerPolicy("headers", "X-Alias-Test", "same-value"));

        ApiService withAlias = basicService("my-api", "my-api");
        withAlias.authentication = auth("jwt");
        withAlias.policies = List.of(headerPolicy("header_modification", "X-Alias-Test", "same-value"));

        String headersRoute = service.convert(withHeaders, "ns").get("httproute.yaml");
        String aliasRoute = service.convert(withAlias, "ns").get("httproute.yaml");

        assertTrue(aliasRoute.contains("ResponseHeaderModifier"),
                "header_modification must emit HeaderModifier like headers");
        assertTrue(aliasRoute.contains("X-Alias-Test"));
        assertEquals(headersRoute, aliasRoute,
                "header_modification must produce identical HeaderModifier YAML to headers");
    }

    @Test
    void convert_headerModification_caseInsensitive() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(headerPolicy("Header_Modification", "X-Case", "ok"));

        String httproute = service.convert(svc, "ns").get("httproute.yaml");
        assertTrue(httproute.contains("ResponseHeaderModifier"));
        assertTrue(httproute.contains("X-Case"));
    }

    @Test
    void convert_bothHeadersAndHeaderModification_usesFirstEnabledDeterministically() {
        // Order: headers first, then header_modification — first match wins (documented).
        ApiService headersFirst = basicService("my-api", "my-api");
        headersFirst.authentication = auth("jwt");
        headersFirst.policies = List.of(
                headerPolicy("headers", "X-First", "from-headers"),
                headerPolicy("header_modification", "X-Second", "from-alias"));

        ApiService aliasFirst = basicService("my-api", "my-api");
        aliasFirst.authentication = auth("jwt");
        aliasFirst.policies = List.of(
                headerPolicy("header_modification", "X-First", "from-alias"),
                headerPolicy("headers", "X-Second", "from-headers"));

        String headersFirstRoute = service.convert(headersFirst, "ns").get("httproute.yaml");
        String aliasFirstRoute = service.convert(aliasFirst, "ns").get("httproute.yaml");

        assertTrue(headersFirstRoute.contains("X-First"));
        assertTrue(headersFirstRoute.contains("from-headers"));
        assertFalse(headersFirstRoute.contains("from-alias"),
                "When both names present, first enabled policy in list order wins");

        assertTrue(aliasFirstRoute.contains("X-First"));
        assertTrue(aliasFirstRoute.contains("from-alias"));
        assertFalse(aliasFirstRoute.contains("from-headers"),
                "When both names present, first enabled policy in list order wins");
    }

    // ── CORS policy → ResponseHeaderModifier (OCP 4.19 / GAPI 1.2.1) + OPTIONS ─

    @Test
    void convert_corsPolicy_emitsResponseHeaderModifierAndOptions() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/users";
        svc.mappingRules = List.of(rule);
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"),
                List.of("GET", "POST"),
                List.of("Authorization", "Content-Type"),
                true,
                600));

        String httproute = service.convert(svc, "ns").get("httproute.yaml");

        // Must NOT use native type: CORS (unsupported on OCP 4.19 Gateway API 1.2.1)
        assertFalse(httproute.contains("type: CORS"),
                "type: CORS requires Gateway API ≥ 1.3; OCP 4.19 ships 1.2.1");
        assertFalse(httproute.contains("\n          cors:"),
                "native cors: block must not appear under OCP 4.19 / RHCL 1.4 minimum");

        // migration-pilot pattern: ResponseHeaderModifier + Access-Control-* + OPTIONS
        assertTrue(httproute.contains("type: ResponseHeaderModifier"));
        assertTrue(httproute.contains("Access-Control-Allow-Origin"));
        assertTrue(httproute.contains("https://app.example.com"));
        assertTrue(httproute.contains("Access-Control-Allow-Methods"));
        assertTrue(httproute.contains("GET, POST") || httproute.contains("GET,POST"));
        assertTrue(httproute.contains("Access-Control-Allow-Headers"));
        assertTrue(httproute.contains("Authorization"));
        assertTrue(httproute.contains("method: OPTIONS"),
                "cors must add OPTIONS preflight on product path(s)");
        assertTrue(httproute.contains("/api/users"));
    }

    @Test
    void convert_corsPolicy_includesCredentialsAndMaxAge() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("*"),
                List.of("GET"),
                List.of(),
                true,
                86400));

        String httproute = service.convert(svc, "ns").get("httproute.yaml");
        assertFalse(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("Access-Control-Allow-Credentials"),
                "credentials must map to Access-Control-Allow-Credentials header");
        assertTrue(httproute.contains("Access-Control-Max-Age"));
        assertTrue(httproute.contains("86400"),
                "max-age must map to Access-Control-Max-Age header value");
    }

    @Test
    void convert_noCorsPolicy_noCorsFiltersOrOptions() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/users";
        svc.mappingRules = List.of(rule);
        svc.policies = List.of(headerPolicy("headers", "X-Only", "v"));

        String httproute = service.convert(svc, "ns").get("httproute.yaml");
        assertFalse(httproute.contains("type: CORS"));
        assertFalse(httproute.contains("Access-Control-Allow-Origin"));
        assertFalse(httproute.contains("method: OPTIONS"),
                "without cors, no CORS-only OPTIONS match should be added");
        assertTrue(httproute.contains("ResponseHeaderModifier"));
    }

    // ── AuthPolicy YAML content ───────────────────────────────────────────────

    @Test
    void convert_jwtAuth_authPolicyContainsJwt() {
        ApiService svc = basicService("my-api", "my-api");
        Authentication auth = auth("jwt");
        auth.oidcIssuerEndpoint = "https://sso.example.com/realms/test";
        svc.authentication = auth;

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertTrue(policy.contains("jwt"));
        assertTrue(policy.contains("sso.example.com"));
    }

    @Test
    void convert_apiKeyAuth_authPolicyContainsApiKey() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("apiKey");
        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertTrue(policy.contains("apiKey") || policy.contains("api-key-auth"));
    }

    @Test
    void convert_noneAuth_authPolicyIsEmpty() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = null;
        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertTrue(policy.contains("authentication: {}") || policy.contains("AuthPolicy"));
    }

    // ── ConfigMap YAML content ────────────────────────────────────────────────

    @Test
    void convert_configMapContainsServiceInfo() {
        ApiService svc = basicService("My Service", "my-service");
        svc.id = "svc-42";
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String cm = files.get("configmap.yaml");
        assertTrue(cm.contains("svc-42"));
        assertTrue(cm.contains("My Service"));
    }

    @Test
    void convert_configMapWithBackendUrl() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", "http://my-svc:8080");
        String cm = files.get("configmap.yaml");
        assertTrue(cm.contains("my-svc:8080"));
    }

    @Test
    void convert_configMapWithServiceBackend() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Backend b = new Backend();
        b.privateEndpoint = "http://backend-service:9090";
        svc.backends = List.of(b);
        Map<String, String> files = service.convert(svc, "ns");
        String cm = files.get("configmap.yaml");
        assertTrue(cm.contains("backend-service:9090"));
    }

    // ── APIProduct YAML content ───────────────────────────────────────────────

    @Test
    void convert_apiProductContainsDisplayName() {
        ApiService svc = basicService("My Great API", "my-great-api");
        svc.description = "A great API for testing";
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String ap = files.get("apiproduct.yaml");
        assertTrue(ap.contains("My Great API"));
        assertTrue(ap.contains("devportal.kuadrant.io"));
    }

    // ── Secret YAML content ───────────────────────────────────────────────────

    @Test
    void convert_apiKeyAuth_secretContainsApiKey() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("apiKey");
        Map<String, String> files = service.convert(svc, "ns");
        String secret = files.get("secret.yaml");
        assertTrue(secret.contains("api_key"));
        assertFalse(secret.contains("REPLACE_ME"));
    }

    @Test
    void convert_jwtAuth_secretContainsPlaceholders() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String secret = files.get("secret.yaml");
        assertTrue(secret.contains("REPLACE_ME"));
    }

    // ── ServiceEntry + DestinationRule ────────────────────────────────────────

    @Test
    void convert_externalBackend_serviceEntryContainsHost() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", "https://api.external.example.com");
        String se = files.get("serviceentry.yaml");
        assertTrue(se.contains("api.external.example.com"));
        assertTrue(se.contains("ServiceEntry"));
    }

    @Test
    void convert_externalBackend_destinationRuleContainsTls() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", "https://api.external.example.com");
        String dr = files.get("destinationrule.yaml");
        assertTrue(dr.contains("SIMPLE") || dr.contains("tls"));
    }

    // ── detectBackendType() ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "http://my-service:8080",
            "http://svc.namespace.svc.cluster.local", "my-service"})
    void detectBackendType_internal(String url) {
        ConversionService.BackendType type = service.detectBackendType(url.isBlank() ? null : url);
        assertEquals(ConversionService.BackendType.INTERNAL, type);
    }

    @ParameterizedTest
    @CsvSource({
        "https://api.example.com, EXTERNAL",
        "https://foo.ecs.us-east-2.on.aws/api, EXTERNAL",
        "http://api.external-provider.com, EXTERNAL",
        "http://svc.cluster.local, EXTERNAL"
    })
    void detectBackendType_external(String url, String expected) {
        ConversionService.BackendType type = service.detectBackendType(url);
        assertEquals(ConversionService.BackendType.valueOf(expected), type);
    }

    @Test
    void detectBackendType_null_isInternal() {
        assertEquals(ConversionService.BackendType.INTERNAL, service.detectBackendType(null));
    }

    // ── README content ────────────────────────────────────────────────────────

    @Test
    void convert_readmeContainsServiceName() {
        ApiService svc = basicService("Customer API", "customer-api");
        svc.id = "cust-1";
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String readme = files.get("README.md");
        assertTrue(readme.contains("Customer API"));
        assertTrue(readme.contains("cust-1"));
    }

    @Test
    void convert_externalReadme_mentionsExternal() {
        ApiService svc = basicService("External API", "external-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", "https://ext.example.com");
        String readme = files.get("README.md");
        assertTrue(readme.contains("ext.example.com") || readme.contains("External"));
    }

    // ── Kebab case conversion ─────────────────────────────────────────────────

    @Test
    void convert_systemName_usedForResourceName() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "My API";
        svc.systemName = "my_api_service";
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        String gw = files.get("gateway.yaml");
        assertTrue(gw.contains("my-api-service"));
    }

    @Test
    void convert_nameUsedWhenSystemNameNull() {
        ApiService svc = new ApiService();
        svc.id = "1";
        svc.name = "My Service";
        svc.systemName = null;
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns");
        assertNotNull(files.get("gateway.yaml"));
    }

    // ── ConversionOptions bag (PR2) ───────────────────────────────────────────

    @Test
    void convert_withOptions_defaultsMatchPositionalOverload() {
        ApiService svc = basicService("Opts API", "opts-api");
        svc.authentication = auth("jwt");

        Map<String, String> viaOverload = service.convert(svc, "ns");
        Map<String, String> viaOptions = service.convert(svc, "ns", null, new ConversionOptions());

        assertEquals(viaOverload.keySet(), viaOptions.keySet());
        assertEquals(viaOverload.get("gateway.yaml"), viaOptions.get("gateway.yaml"));
        assertEquals(viaOverload.get("policy.yaml"), viaOptions.get("policy.yaml"));
    }

    @Test
    void convert_withOptions_loggingTargetWorkload() {
        ApiService svc = basicService("Log API", "log-api");
        svc.authentication = auth("jwt");
        Policy logging = new Policy();
        logging.name = "logging";
        logging.enabled = true;
        logging.configuration = Map.of("enable_access_logs", true);
        svc.policies = List.of(logging);

        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = "workload";
        Map<String, String> files = service.convert(svc, "ns", null, opts);
        String telemetry = files.get("telemetry.yaml");
        assertNotNull(telemetry);
        assertTrue(telemetry.contains("SIDECAR_INBOUND") || telemetry.contains("workload")
                        || !telemetry.contains("GATEWAY"),
                "workload loggingTarget must not use GATEWAY-only selector");
    }

    // ── ip_check → AuthorizationPolicy / AuthPolicy OPA (PR2) ─────────────────

    @Test
    void convert_ipCheck_authorizationPolicyMode_emitsAuthzPolicy() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("203.0.113.10", "198.51.100.0/24")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authorizationPolicy";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertTrue(files.containsKey("authorizationpolicy.yaml"),
                "authorizationPolicy mode must emit authorizationpolicy.yaml");
        String authz = files.get("authorizationpolicy.yaml");
        assertTrue(authz.contains("kind: AuthorizationPolicy"));
        assertTrue(authz.contains("203.0.113.10") || authz.contains("203.0.113.10/32"));
        assertTrue(authz.contains("198.51.100.0/24"));
        assertFalse(authz.toLowerCase().contains("opa"),
                "Authz mode must not embed OPA in AuthorizationPolicy");
        String policy = files.get("policy.yaml");
        assertFalse(policy != null && policy.contains("ip-check") && policy.contains("opa"),
                "authorizationPolicy mode must not emit OPA ip-check in AuthPolicy");
    }

    @Test
    void convert_ipCheck_defaultModeWhenUnset_isAuthorizationPolicy() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("blacklist", List.of("10.0.0.1")));

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("authorizationpolicy.yaml"),
                "Default (unset) ipCheckMode must be authorizationPolicy");
        String authz = files.get("authorizationpolicy.yaml");
        assertTrue(authz.contains("DENY") || authz.contains("deny"),
                "blacklist check_type should map to DENY action");
        assertTrue(authz.contains("10.0.0.1") || authz.contains("10.0.0.1/32"));
    }

    @Test
    void convert_ipCheck_authPolicyOpaMode_emitsOpaOnly() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("192.0.2.1/32")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authPolicyOpa";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertFalse(files.containsKey("authorizationpolicy.yaml"),
                "authPolicyOpa mode must NOT emit authorizationpolicy.yaml");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("opa") || policy.contains("rego"),
                "authPolicyOpa must encode IP allow/deny in AuthPolicy OPA");
        assertTrue(policy.contains("192.0.2.1"));
    }

    @Test
    void convert_noIpCheck_noAuthorizationPolicyFile() {
        ApiService svc = basicService("No IP", "no-ip");
        svc.authentication = auth("jwt");
        svc.policies = List.of();

        Map<String, String> files = service.convert(svc, "ns");
        assertFalse(files.containsKey("authorizationpolicy.yaml"));
    }

    // ── App ID/App Key AuthPolicy + real Secret (PR2) ─────────────────────────

    @Test
    void convert_appIdKey_withRealCredentials_emitsAuthPolicyAndSecret() {
        ApiService svc = basicService("App ID API", "app-id-api");
        svc.authentication = auth("appIdKey");
        Application app = new Application();
        app.id = "42";
        app.name = "Demo App";
        app.appId = "real-app-id-abc";
        app.keys = List.of("real-app-key-xyz");
        svc.applications = List.of(app);

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("AuthPolicy"));
        assertTrue(policy.contains("app-id") || policy.contains("appId") || policy.contains("app_id")
                        || policy.contains("api-key-auth") || policy.contains("app-id-key"),
                "App ID auth must emit AuthPolicy authentication block");

        String secret = files.get("secret.yaml");
        assertNotNull(secret);
        assertTrue(secret.contains("real-app-id-abc"), "Secret must contain real app id");
        assertTrue(secret.contains("real-app-key-xyz"), "Secret must contain real app key");
        assertTrue(secret.contains("app_id_1") || secret.contains("app_id:"),
                "Secret keys should use app_id_N naming");
        assertFalse(secret.contains("REPLACE_ME"), "Must not invent placeholder keys when creds exist");
    }

    @Test
    void convert_appIdKey_missingCredentials_warnsWithoutInventingKeys() {
        ApiService svc = basicService("App ID API", "app-id-api");
        svc.authentication = auth("appIdKey");
        svc.applications = null;

        Map<String, String> files = service.convert(svc, "ns");
        String secret = files.get("secret.yaml");
        assertNotNull(secret);
        assertTrue(secret.contains("WARNING") || secret.toLowerCase().contains("warn")
                        || files.get("README.md").toLowerCase().contains("warn"),
                "Missing App ID credentials must produce a warning");
        assertFalse(secret.contains("fake-") || secret.contains("invented"),
                "Must not invent credential values");
        // Must not invent REPLACE_ME app keys for appIdKey path
        assertFalse(secret.contains("app_key: \"REPLACE_ME\"")
                || secret.contains("app_id: \"REPLACE_ME\""));
    }

    @Test
    void convert_appIdKey_multipleApps_oneSecretWithIndexedKeys() {
        ApiService svc = basicService("Multi App", "multi-app");
        svc.authentication = auth("appIdKey");
        Application a1 = new Application();
        a1.id = "1";
        a1.appId = "id-one";
        a1.keys = List.of("key-one");
        Application a2 = new Application();
        a2.id = "2";
        a2.appId = "id-two";
        a2.keys = List.of("key-two");
        svc.applications = List.of(a1, a2);

        String secret = service.convert(svc, "ns").get("secret.yaml");
        assertTrue(secret.contains("app_id_1") && secret.contains("id-one"));
        assertTrue(secret.contains("app_key_1") && secret.contains("key-one"));
        assertTrue(secret.contains("app_id_2") && secret.contains("id-two"));
        assertTrue(secret.contains("app_key_2") && secret.contains("key-two"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ApiService basicService(String name, String systemName) {
        ApiService svc = new ApiService();
        svc.id = "svc-1";
        svc.name = name;
        svc.systemName = systemName;
        return svc;
    }

    private Authentication auth(String type) {
        Authentication a = new Authentication();
        a.type = type;
        return a;
    }

    private Policy headerPolicy(String name, String header, String value) {
        Policy p = new Policy();
        p.name = name;
        p.enabled = true;
        Map<String, Object> entry = new HashMap<>();
        entry.put("header", header);
        entry.put("value", value);
        entry.put("op", "set");
        entry.put("value_type", "plain");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("response", List.of(entry));
        p.configuration = cfg;
        return p;
    }

    private Policy corsPolicy(List<String> origins, List<String> methods, List<String> headers,
                              boolean credentials, int maxAge) {
        Policy p = new Policy();
        p.name = "cors";
        p.enabled = true;
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("allow_origin", origins);
        cfg.put("allow_methods", methods);
        cfg.put("allow_headers", headers);
        cfg.put("allow_credentials", credentials);
        cfg.put("max_age", maxAge);
        p.configuration = cfg;
        return p;
    }

    private Policy ipCheckPolicy(String checkType, List<String> ips) {
        Policy p = new Policy();
        p.name = "ip_check";
        p.enabled = true;
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("check_type", checkType);
        cfg.put("ips", ips);
        cfg.put("error_msg", "IP not allowed");
        cfg.put("client_ip_sources", List.of("X-Forwarded-For", "X-Real-IP"));
        p.configuration = cfg;
        return p;
    }
}
