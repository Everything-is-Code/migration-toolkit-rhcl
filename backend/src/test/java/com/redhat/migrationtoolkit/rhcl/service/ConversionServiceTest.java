package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApplicationPlan;
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
    void convert_httpRouteYaml_bracePatternBecomesPathPrefix() {
        // 3scale patterns like /api/{?} are sanitized for Gateway API PathPrefix by
        // truncating at '{'; a literal '*' would be misleading (PathPrefix is not a glob).
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/{?}";
        svc.mappingRules = List.of(rule);

        Map<String, String> files = service.convert(svc, "ns");
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("type: PathPrefix"), "brace patterns must use PathPrefix");
        assertTrue(httproute.contains("value: \"/api\""), "prefix should truncate at '{'");
        assertFalse(httproute.contains("{?}"), "brace wildcards must not appear in HTTPRoute");
        assertFalse(httproute.contains("value: \"/api*\""), "must not emit a literal '*' PathPrefix");
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

    // ── CORS policy → RHM+OPTIONS (corsNative=false) or type: CORS (corsNative=true) ─

    @ParameterizedTest(name = "corsNative={0}")
    @ValueSource(booleans = {false, true})
    void convert_corsPolicy_branchesOnCorsNative(boolean corsNative) {
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

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = corsNative;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");

        assertTrue(httproute.contains("method: OPTIONS"),
                "cors must add OPTIONS preflight on product path(s)");
        assertTrue(httproute.contains("/api/users"));
        assertTrue(httproute.contains("https://app.example.com"));

        if (corsNative) {
            assertTrue(httproute.contains("type: CORS"),
                    "corsNative=true must emit Gateway API CORS filter");
            assertTrue(httproute.contains("\n          cors:"));
            assertTrue(httproute.contains("allowOrigins:"));
            assertTrue(httproute.contains("allowMethods:"));
            assertFalse(httproute.contains("Access-Control-Allow-Origin"),
                    "native CORS must not use ResponseHeaderModifier Access-Control-* for CORS");
        } else {
            // Default / OCP 4.19 / GAPI 1.2.1 path
            assertFalse(httproute.contains("type: CORS"),
                    "type: CORS requires Gateway API ≥ 1.3; OCP 4.19 ships 1.2.1");
            assertFalse(httproute.contains("\n          cors:"),
                    "native cors: block must not appear when corsNative=false");
            assertTrue(httproute.contains("type: ResponseHeaderModifier"));
            assertTrue(httproute.contains("Access-Control-Allow-Origin"));
            assertTrue(httproute.contains("Access-Control-Allow-Methods"));
            assertTrue(httproute.contains("GET, POST") || httproute.contains("GET,POST"));
            assertTrue(httproute.contains("Access-Control-Allow-Headers"));
            assertTrue(httproute.contains("Authorization"));
        }
    }

    @Test
    void convert_corsPolicy_defaultOptions_neverEmitsNativeCors() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"),
                List.of("GET"),
                List.of("Authorization"),
                false,
                600));

        String httproute = service.convert(svc, "ns").get("httproute.yaml");
        assertFalse(httproute.contains("type: CORS"),
                "ConversionOptions.corsNative defaults false — never emit type: CORS");
        assertTrue(httproute.contains("type: ResponseHeaderModifier"));
    }

    @Test
    void convert_corsPolicy_includesCredentialsAndMaxAge_fallback() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("*"),
                List.of("GET"),
                List.of(),
                true,
                86400));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = false;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");
        assertFalse(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("Access-Control-Allow-Credentials"),
                "credentials must map to Access-Control-Allow-Credentials header");
        assertTrue(httproute.contains("Access-Control-Max-Age"));
        assertTrue(httproute.contains("86400"),
                "max-age must map to Access-Control-Max-Age header value");
        assertTrue(httproute.contains("value: \"*\""),
                "wildcard Allow-Origin must be YAML-quoted (bare * is an alias)");
        assertFalse(httproute.matches("(?s).*value:\\s+\\*(?:\\s|$).*"),
                "must not emit unquoted value: *");
    }

    @Test
    void convert_corsPolicy_credentialsIndent_matchesSiblingAccessControlHeaders() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"),
                List.of("GET", "POST"),
                List.of("Authorization"),
                true,
                600));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = false;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");

        String originItem = "              - name: Access-Control-Allow-Origin";
        String credentialsItem = "              - name: Access-Control-Allow-Credentials";
        String credentialsValue = "                value: \"true\"";
        assertTrue(httproute.contains(originItem),
                "Allow-Origin sibling must use 14-space list-item indent");
        assertTrue(httproute.contains(credentialsItem),
                "Allow-Credentials must align at 14 spaces like sibling Access-Control-* headers");
        assertTrue(httproute.contains(credentialsItem + "\n" + credentialsValue),
                "Allow-Credentials value must use 16-space indent matching sibling headers");
        assertFalse(httproute.contains("                  - name: Access-Control-Allow-Credentials"),
                "must not over-indent Allow-Credentials relative to siblings");
    }

    @Test
    void convert_corsPolicy_credentialsIndent_withMaxAgeStillAligned() {
        ApiService svc = basicService("cors-age", "cors-age");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"),
                List.of("GET"),
                List.of(),
                true,
                3600));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = false;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");

        assertTrue(httproute.contains(
                        "              - name: Access-Control-Allow-Credentials\n"
                                + "                value: \"true\"\n"
                                + "              - name: Access-Control-Max-Age"),
                "credentials block must sit between siblings at the same indent as Max-Age");
    }

    @Test
    void convert_corsPolicy_wildcardOrigin_nativeIsYamlQuoted() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("*"),
                List.of("GET", "POST", "OPTIONS"),
                List.of("Authorization", "Content-Type"),
                true,
                600));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = true;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");

        assertTrue(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("allowOrigins:\n              - \"*\""),
                "native CORS wildcard origin must be double-quoted for valid YAML");
        assertFalse(httproute.contains("allowOrigins:\n              - *\n"),
                "unquoted - * is invalid YAML (alias indicator)");
        assertTrue(httproute.contains("- \"Authorization\""));
        assertTrue(httproute.contains("- \"Content-Type\""));
    }

    @Test
    void yamlDoubleQuoted_escapesBackslashAndQuotes() {
        assertEquals("\"*\"", ConversionService.yamlDoubleQuoted("*"));
        assertEquals("\"https://app.example.com\"",
                ConversionService.yamlDoubleQuoted("https://app.example.com"));
        assertEquals("\"a\\\"b\"", ConversionService.yamlDoubleQuoted("a\"b"));
        assertEquals("\"a\\\\b\"", ConversionService.yamlDoubleQuoted("a\\b"));
        assertEquals("\"\"", ConversionService.yamlDoubleQuoted(null));
    }

    @Test
    void convert_corsPolicy_includesCredentialsAndMaxAge_native() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"),
                List.of("GET"),
                List.of(),
                true,
                86400));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = true;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");
        assertTrue(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("allowCredentials: true"));
        assertTrue(httproute.contains("maxAge: 86400"));
        assertFalse(httproute.contains("Access-Control-Allow-Credentials"));
    }

    @Test
    void convert_corsNativeFromProfile421_matrixOnly_noClusterSideEffects() {
        // Profile override only widens emit caps; convert path needs no Kubernetes client.
        var caps = ClusterVersionService.capabilitiesFrom("4.21.0", "1.3.0", null, null, null);
        assertTrue(caps.corsNative);

        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"), List.of("GET"), List.of(), false, 60));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = caps.corsNative;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");
        assertTrue(httproute.contains("type: CORS"));
        assertFalse(httproute.contains("Access-Control-Allow-Origin"));
    }

    @Test
    void convert_corsNativeFromProfile419_matrixFallback() {
        var caps = ClusterVersionService.capabilitiesFrom("4.19.0", "1.2.1", null, null, null);
        assertFalse(caps.corsNative);

        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(corsPolicy(
                List.of("https://app.example.com"), List.of("GET"), List.of(), false, 60));

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = caps.corsNative;
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");
        assertFalse(httproute.contains("type: CORS"));
        assertTrue(httproute.contains("ResponseHeaderModifier"));
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

        ConversionOptions opts = new ConversionOptions();
        opts.corsNative = true; // even with native capability, no CORS policy → no CORS filter
        String httproute = service.convert(svc, "ns", null, opts).get("httproute.yaml");
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
    void convert_ipCheck_authPolicyOpaMode_usesSourceAddressNotXff() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("192.0.2.1/32")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authPolicyOpa";
        String policy = service.convert(svc, "ns", null, opts).get("policy.yaml");

        assertNotNull(policy);
        assertTrue(policy.contains("input.source.address"),
                "OPA Rego must use Authorino WellKnown input.source.address");
        assertFalse(policy.contains("input.attributes.source.address"),
                "OPA Rego must not use Envoy input.attributes.source.address");
        assertFalse(policy.contains("input.attributes"),
                "OPA Rego must not bind client IP via input.attributes");
        assertFalse(policy.contains("x-forwarded-for"),
                "OPA Rego must not use spoofable X-Forwarded-For header");
        assertFalse(policy.contains("X-Forwarded-For"));
    }

    @Test
    void convert_ipCheck_authPolicyOpaMode_blacklistAlsoUsesSourceAddress() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("blacklist", List.of("10.0.0.1")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authPolicyOpa";
        String policy = service.convert(svc, "ns", null, opts).get("policy.yaml");

        assertNotNull(policy);
        assertTrue(policy.contains("input.source.address"),
                "OPA Rego must use Authorino WellKnown input.source.address");
        assertFalse(policy.contains("input.attributes.source.address"),
                "OPA Rego must not use Envoy input.attributes.source.address");
        assertFalse(policy.contains("input.attributes"),
                "OPA Rego must not bind client IP via input.attributes");
        assertFalse(policy.contains("x-forwarded-for"));
        assertTrue(policy.contains("denied") || policy.contains("not denied"),
                "blacklist path must still emit deny Rego");
    }

    @Test
    void convert_ipCheck_blankCidrSkipped_authzKeepsValidEntry() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("", "  ", "203.0.113.10")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authorizationPolicy";
        String authz = service.convert(svc, "ns", null, opts).get("authorizationpolicy.yaml");

        assertNotNull(authz);
        assertTrue(authz.contains("203.0.113.10") || authz.contains("203.0.113.10/32"));
        // Blank entries must not collapse to allow-all while a valid CIDR exists
        assertFalse(authz.contains("0.0.0.0/0"),
                "blank CIDRs must be skipped; must not emit 0.0.0.0/0 alongside valid IPs");
    }

    @Test
    void convert_ipCheck_blankCidrSkipped_opaKeepsValidEntry() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("", "192.0.2.1/32")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authPolicyOpa";
        String policy = service.convert(svc, "ns", null, opts).get("policy.yaml");

        assertNotNull(policy);
        assertTrue(policy.contains("192.0.2.1"));
        assertFalse(policy.contains("0.0.0.0/0"),
                "blank CIDR must not become allow-all in OPA cidrs list");
    }

    @Test
    void convert_ipCheck_allBlankCidrs_authzAllowAllOnlyWhenEmpty() {
        ApiService svc = basicService("IP API", "ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(ipCheckPolicy("whitelist", List.of("", "   ")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authorizationPolicy";
        String authz = service.convert(svc, "ns", null, opts).get("authorizationpolicy.yaml");

        assertNotNull(authz);
        assertTrue(authz.contains("0.0.0.0/0"),
                "empty-after-filter AuthorizationPolicy keeps intentional allow-all");
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
    void convert_apiKeyAuth_secretSelectorIsNamespaceScoped() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("apiKey");
        String policy = service.convert(svc, "ns").get("policy.yaml");

        assertNotNull(policy);
        assertTrue(policy.contains("api-key-auth") || policy.contains("apiKey"));
        assertFalse(policy.contains("allNamespaces"),
                "apiKey AuthPolicy Secret selector must be namespace-scoped");
        assertTrue(policy.contains("selector:") && policy.contains("matchLabels:"),
                "apiKey AuthPolicy must retain label selector");
    }

    @Test
    void convert_appIdKey_secretSelectorIsNamespaceScoped() {
        ApiService svc = basicService("App ID API", "app-id-api");
        svc.authentication = auth("appIdKey");
        Application app = new Application();
        app.id = "42";
        app.appId = "real-app-id-abc";
        app.keys = List.of("real-app-key-xyz");
        svc.applications = List.of(app);

        String policy = service.convert(svc, "ns").get("policy.yaml");

        assertNotNull(policy);
        assertTrue(policy.contains("app-id-key") || policy.contains("app-id-key-auth"));
        assertFalse(policy.contains("allNamespaces"),
                "appIdKey AuthPolicy Secret selector must be namespace-scoped");
        assertTrue(policy.contains("selector:") && policy.contains("matchLabels:"));
    }

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

    // ── edge_limiting ∪ plan limits → RateLimitPolicy (PR3) ───────────────────

    @Test
    void convert_edgeLimitingAndPlanLimits_emitsUnionRateLimitPolicy() {
        ApiService svc = basicService("Rate API", "rate-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(edgeLimitingPolicy(30, 60));
        ApplicationPlan plan = new ApplicationPlan();
        plan.id = "1";
        plan.name = "Gold";
        plan.systemName = "gold";
        plan.limits = List.of(Map.of("period", "minute", "value", 200, "metric_system_name", "hits"));
        svc.applicationPlans = List.of(plan);

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("ratelimitpolicy.yaml"),
                "Both sources must emit ratelimitpolicy.yaml");
        String rlp = files.get("ratelimitpolicy.yaml");
        assertTrue(rlp.contains("kind: RateLimitPolicy"));
        assertTrue(rlp.contains("targetRef") && rlp.contains("HTTPRoute"),
                "RateLimitPolicy must target HTTPRoute");
        // Named limit from edge_limiting policy
        assertTrue(rlp.contains("30") && (rlp.contains("60s") || rlp.contains("60")),
                "Policy fixed-window count/window must appear in rates");
        // Global ceiling from plan (prefer plan minute over hardcoded 100/60s)
        assertTrue(rlp.contains("global") && rlp.contains("200"),
                "Plan max minute ceiling must appear as global limit");
        assertFalse(rlp.contains("kind: PlanPolicy"), "v1 emits RateLimitPolicy only, no PlanPolicy");
    }

    @Test
    void convert_edgeLimitingOnly_emitsRateLimitFromPolicy() {
        ApiService svc = basicService("Edge Only", "edge-only");
        svc.authentication = auth("jwt");
        svc.policies = List.of(edgeLimitingPolicy(15, 30));
        svc.applicationPlans = List.of();

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("ratelimitpolicy.yaml"));
        String rlp = files.get("ratelimitpolicy.yaml");
        assertTrue(rlp.contains("RateLimitPolicy"));
        assertTrue(rlp.contains("15"));
        assertFalse(rlp.contains("\nglobal:") || rlp.matches("(?s).*\\bglobal:\\s*\\n.*rates:.*"),
                "Policy-only path should not invent a plan global ceiling");
    }

    @Test
    void convert_planLimitsOnly_emitsGlobalFromPlanCeiling() {
        ApiService svc = basicService("Plan Only", "plan-only");
        svc.authentication = auth("jwt");
        svc.policies = List.of();
        ApplicationPlan planA = new ApplicationPlan();
        planA.id = "1";
        planA.limits = List.of(Map.of("period", "hour", "value", 1000));
        ApplicationPlan planB = new ApplicationPlan();
        planB.id = "2";
        planB.limits = List.of(
                Map.of("period", "minute", "value", 50),
                Map.of("period", "minute", "value", 80));
        svc.applicationPlans = List.of(planA, planB);

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("ratelimitpolicy.yaml"));
        String rlp = files.get("ratelimitpolicy.yaml");
        assertTrue(rlp.contains("global"));
        // Prefer highest minute (80) over hour when minute exists
        assertTrue(rlp.contains("80"), "Must prefer max plan minute ceiling");
        assertFalse(rlp.contains("limit: 100") && rlp.contains("60s") && !rlp.contains("80"),
                "Must not fall back to hardcoded 100/60s when plan data exists");
    }

    @Test
    void convert_neitherEdgeLimitingNorPlans_noRateLimitPolicyFile() {
        ApiService svc = basicService("No Limits", "no-limits");
        svc.authentication = auth("jwt");
        svc.policies = List.of();
        svc.applicationPlans = null;

        Map<String, String> files = service.convert(svc, "ns");
        assertFalse(files.containsKey("ratelimitpolicy.yaml"),
                "Neither source → no RateLimitPolicy file");
    }

    @Test
    void convert_connectionLimiters_emitsWarningInRateLimitAndReadme() {
        ApiService svc = basicService("Conn Limit", "conn-limit");
        svc.authentication = auth("jwt");
        svc.policies = List.of(edgeLimitingConnectionPolicy(25));
        svc.applicationPlans = List.of();

        Map<String, String> files = service.convert(svc, "ns");
        String rlp = files.get("ratelimitpolicy.yaml");
        String readme = files.get("README.md");
        assertNotNull(rlp);
        assertTrue(rlp.contains("# WARNING:") && rlp.toLowerCase().contains("connection"),
                "connection_limiters→rate approximation must warn in RateLimitPolicy YAML");
        assertTrue(readme != null && readme.contains("WARNING")
                        && readme.toLowerCase().contains("connection"),
                "connection_limiters approximation must warn in README");
        assertTrue(rlp.contains("25") && rlp.contains("window: 1s"));
    }

    @Test
    void convert_leakyBucket_emitsWarningInRateLimitAndReadme() {
        ApiService svc = basicService("Leaky", "leaky");
        svc.authentication = auth("jwt");
        svc.policies = List.of(edgeLimitingLeakyBucketPolicy(40));
        svc.applicationPlans = List.of();

        Map<String, String> files = service.convert(svc, "ns");
        String rlp = files.get("ratelimitpolicy.yaml");
        String readme = files.get("README.md");
        assertNotNull(rlp);
        assertTrue(rlp.contains("# WARNING:") && rlp.toLowerCase().contains("leaky"),
                "leaky_bucket→fixed window approximation must warn in RateLimitPolicy YAML");
        assertTrue(readme != null && readme.contains("WARNING")
                        && readme.toLowerCase().contains("leaky"),
                "leaky_bucket approximation must warn in README");
        assertTrue(rlp.contains("40") && rlp.contains("window: 1s"));
    }

    @Test
    void convert_planCeiling_emitsMaxAcrossPlansWarningInYamlAndReadme() {
        ApiService svc = basicService("Ceiling", "ceiling");
        svc.authentication = auth("jwt");
        svc.policies = List.of();
        ApplicationPlan planA = new ApplicationPlan();
        planA.id = "1";
        planA.limits = List.of(Map.of("period", "minute", "value", 10));
        ApplicationPlan planB = new ApplicationPlan();
        planB.id = "2";
        planB.limits = List.of(Map.of("period", "minute", "value", 90));
        svc.applicationPlans = List.of(planA, planB);

        Map<String, String> files = service.convert(svc, "ns");
        String rlp = files.get("ratelimitpolicy.yaml");
        String readme = files.get("README.md");
        assertNotNull(rlp);
        assertTrue(rlp.contains("90"));
        assertTrue(rlp.contains("# WARNING:")
                        && (rlp.toLowerCase().contains("plan") || rlp.toLowerCase().contains("ceiling")),
                "plan ceiling = max across all plans must warn in RateLimitPolicy YAML");
        assertTrue(readme != null && readme.contains("WARNING")
                        && (readme.toLowerCase().contains("plan") || readme.toLowerCase().contains("ceiling")),
                "plan-ceiling max-all-plans note must appear in README");
    }

    // ── token_introspection → AuthPolicy oauth2Introspection (PR4) ────────────

    @Test
    void convert_tokenIntrospection_withUrl_emitsOauth2Introspection() {
        ApiService svc = basicService("Introspect API", "introspect-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(tokenIntrospectionPolicy(
                "https://sso.example.com/token/introspect",
                "access_token",
                "my-client",
                "my-secret"));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("AuthPolicy"));
        assertTrue(policy.contains("oauth2Introspection"),
                "token_introspection with URL must emit oauth2Introspection");
        assertTrue(policy.contains("https://sso.example.com/token/introspect")
                        || policy.contains("introspectionEndpoint")
                        || policy.contains("endpoint:"),
                "Must map introspection URL into AuthPolicy");
        assertTrue(policy.contains("tokenTypeHint") || policy.contains("access_token"),
                "Must map tokenTypeHint when present");
        assertTrue(policy.contains("credentialsRef") || policy.contains("client"),
                "Must reference credentials from policy config / Secret");
    }

    @Test
    void convert_tokenIntrospection_incomplete_warnsWithoutFullSupport() {
        ApiService svc = basicService("Incomplete Introspect", "incomplete-introspect");
        svc.authentication = auth("jwt");
        Policy incomplete = new Policy();
        incomplete.name = "token_introspection";
        incomplete.enabled = true;
        incomplete.configuration = new HashMap<>();
        incomplete.configuration.put("auth_type", "client_id+client_secret");
        // missing introspection_url
        svc.policies = List.of(incomplete);

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String secret = files.get("secret.yaml");
        String readme = files.get("README.md");
        assertFalse(policy != null && policy.contains("oauth2Introspection"),
                "Incomplete token_introspection must NOT emit full oauth2Introspection");
        assertFalse(secret != null && secret.contains("oauth2-introspection"),
                "Incomplete token_introspection must NOT emit oauth2-introspection Secret");
        assertTrue(secret != null && secret.contains("incomplete-introspect-credentials"),
                "Without introspection URL, Secret must fall through to JWT/auth-type credentials");
        assertTrue((readme != null && readme.toLowerCase().contains("warn"))
                        || (policy != null && policy.contains("WARNING")),
                "Incomplete config must warn and not claim full support");
    }

    @Test
    void convert_tokenIntrospection_incomplete_apiKey_fallsThroughToApiKeySecret() {
        ApiService svc = basicService("Incomplete ApiKey", "incomplete-apikey");
        svc.authentication = auth("apiKey");
        Policy incomplete = new Policy();
        incomplete.name = "token_introspection";
        incomplete.enabled = true;
        incomplete.configuration = new HashMap<>();
        // missing introspection_url — same gate as AuthPolicy
        svc.policies = List.of(incomplete);

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String secret = files.get("secret.yaml");
        assertFalse(policy.contains("oauth2Introspection"));
        assertFalse(secret.contains("oauth2-introspection"),
                "Secret kind must match AuthPolicy fallthrough (no mismatched oauth2 Secret)");
        assertTrue(secret.contains("incomplete-apikey-api-key")
                        || secret.contains("api_key:"),
                "Fallthrough auth-type apiKey must emit api-key Secret");
    }

    @Test
    void convert_tokenIntrospection_mapsCredentialsIntoSecret() {
        ApiService svc = basicService("Creds Introspect", "creds-introspect");
        svc.authentication = auth("jwt");
        svc.policies = List.of(tokenIntrospectionPolicy(
                "https://idp.example.com/introspect",
                null,
                "real-client-id",
                "real-client-secret"));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String secret = files.get("secret.yaml");
        assertTrue(policy.contains("oauth2Introspection"));
        assertTrue(secret.contains("real-client-id"), "Secret must hold real client id");
        assertTrue(secret.contains("real-client-secret"), "Secret must hold real client secret");
        assertTrue(policy.contains("credentialsRef") || policy.contains("creds-introspect"),
                "AuthPolicy must reference the credentials Secret");
    }

    @Test
    void convert_packageGrows_corsIpCheckEdgeLimiting_keepsPriorKinds() {
        // Cross-PR gate 5.1: new kinds appear without dropping existing package files
        ApiService svc = basicService("Combo API", "combo-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(
                corsPolicy(List.of("https://a.example"), List.of("GET"), List.of("X-Req"), false, 600),
                ipCheckPolicy("whitelist", List.of("10.0.0.0/8")),
                edgeLimitingPolicy(10, 60));

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("gateway.yaml"));
        assertTrue(files.containsKey("httproute.yaml"));
        assertTrue(files.containsKey("policy.yaml"));
        assertTrue(files.containsKey("secret.yaml"));
        assertTrue(files.containsKey("configmap.yaml"));
        assertTrue(files.containsKey("apiproduct.yaml"));
        assertTrue(files.containsKey("README.md"));
        String httproute = files.get("httproute.yaml");
        assertTrue(httproute.contains("Access-Control")
                        || httproute.contains("ResponseHeaderModifier")
                        || httproute.contains("CORS")
                        || httproute.contains("allowOrigins"),
                "CORS conversion must still be present (ResponseHeaderModifier or CORS filter)");
        assertTrue(files.containsKey("authorizationpolicy.yaml"),
                "ip_check AuthzPolicy must still be present");
        assertTrue(files.containsKey("ratelimitpolicy.yaml"),
                "edge_limiting RateLimitPolicy must still be present");
    }

    // ── jwt_claim_check → AuthPolicy patternMatching (#20) ───────────────────

    @Test
    void convert_jwtClaimCheck_equals_emitsPatternMatchingEq() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.authentication.oidcIssuerEndpoint = "https://sso.example.com/realms/demo";
        svc.policies = List.of(jwtClaimCheckPolicy(List.of(
                jwtClaimOp("role", "==", "admin", "plain", "plain"))));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("authorization:"), "Must emit authorization block");
        assertTrue(policy.contains("jwt-claim-check"), "Must name the claim-check rule");
        assertTrue(policy.contains("patternMatching"), "Must use Authorino patternMatching");
        assertTrue(policy.contains("selector: auth.identity.role"),
                "Selector must be auth.identity.<claim>");
        assertTrue(policy.contains("operator: eq"), "== must map to eq");
        assertTrue(policy.contains("value: \"admin\"") || policy.contains("value: admin"),
                "Must emit claim value");
    }

    @Test
    void convert_jwtClaimCheck_notEquals_emitsPatternMatchingNeq() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(List.of(
                jwtClaimOp("scope", "!=", "guest", "plain", "plain"))));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("selector: auth.identity.scope"));
        assertTrue(policy.contains("operator: neq"), "!= must map to neq");
        assertTrue(policy.contains("guest"));
    }

    @Test
    void convert_jwtClaimCheck_matches_emitsPatternMatchingMatches() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(List.of(
                jwtClaimOp("email", "matches", ".*@example.com", "plain", "plain"))));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("selector: auth.identity.email"));
        assertTrue(policy.contains("operator: matches"), "matches must map to matches");
        assertTrue(policy.contains(".*@example.com"));
    }

    @Test
    void convert_jwtClaimCheck_andOps_emitMultiplePatternsUnderOneRule() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(List.of(
                jwtClaimOp("role", "==", "admin", "plain", "plain"),
                jwtClaimOp("tenant", "==", "acme", "plain", "plain"))));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("jwt-claim-check"));
        assertTrue(policy.contains("auth.identity.role"));
        assertTrue(policy.contains("auth.identity.tenant"));
        assertEquals(1, policy.split("jwt-claim-check:").length - 1,
                "AND ops must share one named jwt-claim-check rule");
    }

    @Test
    void convert_jwtClaimCheck_liquidClaim_skippedWithReadmeWarning() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(List.of(
                jwtClaimOp("{{ jwt.role }}", "==", "admin", "liquid", "plain"))));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String readme = files.get("README.md");
        assertFalse(policy != null && policy.contains("jwt-claim-check"),
                "liquid claim ops must not emit patternMatching");
        assertTrue(readme != null && readme.contains("WARNING")
                        && readme.toLowerCase().contains("liquid"),
                "README must WARNING about liquid jwt_claim_check");
    }

    @Test
    void convert_jwtClaimCheck_combineOpOr_skippedWithReadmeWarning() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(
                List.of(jwtClaimOp("role", "==", "admin", "plain", "plain")),
                "or", "/", List.of("ANY"), "plain"));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String readme = files.get("README.md");
        assertFalse(policy != null && policy.contains("jwt-claim-check"),
                "combine_op=or rules must be skipped");
        assertTrue(readme != null && readme.contains("WARNING")
                        && (readme.contains("combine_op") || readme.toLowerCase().contains(" or ")),
                "README must WARNING about combine_op=or");
    }

    @Test
    void convert_jwtClaimCheck_pathScoped_emitsGlobalPatternsWithReadmeWarning() {
        ApiService svc = basicService("Claim API", "claim-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(jwtClaimCheckPolicy(
                List.of(jwtClaimOp("role", "==", "admin", "plain", "plain")),
                "and", "/admin/.*", List.of("GET"), "plain"));

        Map<String, String> files = service.convert(svc, "ns");
        String policy = files.get("policy.yaml");
        String readme = files.get("README.md");
        assertNotNull(policy);
        assertTrue(policy.contains("selector: auth.identity.role"),
                "path-scoped rules still emit global claim patterns");
        assertTrue(policy.contains("operator: eq"));
        assertTrue(readme != null && readme.contains("WARNING")
                        && (readme.toLowerCase().contains("path") || readme.toLowerCase().contains("method")),
                "README must WARNING about path/method gating limitations");
    }

    @Test
    void convert_jwtClaimCheck_mergesWithOpaIpCheck() {
        ApiService svc = basicService("Claim+IP API", "claim-ip-api");
        svc.authentication = auth("jwt");
        svc.policies = List.of(
                jwtClaimCheckPolicy(List.of(jwtClaimOp("role", "==", "admin", "plain", "plain"))),
                ipCheckPolicy("whitelist", List.of("192.0.2.1/32")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authPolicyOpa";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertFalse(files.containsKey("authorizationpolicy.yaml"),
                "authPolicyOpa must not emit separate AuthorizationPolicy");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("jwt-claim-check"),
                "jwt_claim_check patternMatching must remain after OPA merge");
        assertTrue(policy.contains("patternMatching"));
        assertTrue(policy.contains("selector: auth.identity.role"));
        assertTrue(policy.contains("ip-check") || policy.contains("opa"),
                "ip_check OPA must coexist with claim-check authorization");
        assertTrue(policy.contains("192.0.2.1"));
    }

    @Test
    void convert_jwtClaimCheck_withIpCheckAuthorizationPolicyMode_keepsSeparateAuthz() {
        ApiService svc = basicService("Claim+IP Authz", "claim-ip-authz");
        svc.authentication = auth("jwt");
        svc.policies = List.of(
                jwtClaimCheckPolicy(List.of(jwtClaimOp("role", "==", "admin", "plain", "plain"))),
                ipCheckPolicy("whitelist", List.of("10.0.0.0/8")));

        ConversionOptions opts = new ConversionOptions();
        opts.ipCheckMode = "authorizationPolicy";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertTrue(files.containsKey("authorizationpolicy.yaml"),
                "authorizationPolicy mode still emits authorizationpolicy.yaml");
        String policy = files.get("policy.yaml");
        assertNotNull(policy);
        assertTrue(policy.contains("jwt-claim-check"));
        assertFalse(policy.contains("ip-check") && policy.contains("opa"),
                "authorizationPolicy mode must not put OPA ip-check in AuthPolicy");
    }

    // ── content_limits → EnvoyFilter request + response honesty (#22 PR-A) ────

    @Test
    void convert_contentLimits_requestShortKey_emitsEnvoyFilterMaxBytes() {
        ApiService svc = basicService("Limits API", "limits-api");
        svc.policies = List.of(contentLimitsPolicy(Map.of("request", 1024)));

        Map<String, String> files = service.convert(svc, "ns");
        assertTrue(files.containsKey("envoyfilter-content-limits.yaml"),
                "Must emit envoyfilter-content-limits.yaml for request limit");
        String ef = files.get("envoyfilter-content-limits.yaml");
        assertTrue(ef.contains("max_request_bytes: 1024")
                        || ef.contains("maxRequestBytes: 1024"),
                "EnvoyFilter must enforce request body limit 1024");
        assertTrue(ef.contains("3scale-migration/source: content_limits")
                        || ef.contains("content_limits"),
                "Must annotate source as content_limits");
    }

    @Test
    void convert_contentLimits_requestContentLimitAlias_emitsEnvoyFilter() {
        ApiService svc = basicService("Limits API", "limits-api");
        svc.policies = List.of(contentLimitsPolicy(Map.of("request_content_limit", 2048)));

        Map<String, String> files = service.convert(svc, "ns");
        String ef = files.get("envoyfilter-content-limits.yaml");
        assertNotNull(ef, "Alias request_content_limit must emit EnvoyFilter");
        assertTrue(ef.contains("2048"), "Request limit must use alias value 2048");
    }

    @Test
    void convert_contentLimits_responseOnly_warnsWithoutHardResponseFilter() {
        ApiService svc = basicService("Limits API", "limits-api");
        svc.policies = List.of(contentLimitsPolicy(Map.of("response", 4096)));

        Map<String, String> files = service.convert(svc, "ns");
        String ef = files.get("envoyfilter-content-limits.yaml");
        assertTrue(ef == null || !ef.contains("max_response") && !ef.contains("response_bytes"),
                "Must NOT emit hard response body Envoy enforcement");
        String route = files.get("httproute.yaml");
        assertNotNull(route);
        assertTrue(route.contains("3scale-migration/response-content-limit")
                        && route.contains("4096"),
                "HTTPRoute must annotate response-content-limit");
        String readme = files.get("README.md");
        assertTrue(readme != null && readme.contains("WARNING")
                        && readme.toLowerCase().contains("response"),
                "README must WARNING about response content limit gap");
    }

    @Test
    void convert_contentLimits_responseContentLimitAlias_annotatesAndWarns() {
        ApiService svc = basicService("Limits API", "limits-api");
        svc.policies = List.of(contentLimitsPolicy(Map.of("response_content_limit", 8192)));

        Map<String, String> files = service.convert(svc, "ns");
        String route = files.get("httproute.yaml");
        assertTrue(route != null && route.contains("8192")
                        && route.contains("response-content-limit"),
                "Alias response_content_limit must annotate HTTPRoute");
        String readme = files.get("README.md");
        assertTrue(readme != null && readme.contains("WARNING"));
    }

    @Test
    void convert_contentLimits_requestAndResponse_emitsFilterAndAnnotation() {
        ApiService svc = basicService("Limits API", "limits-api");
        svc.policies = List.of(contentLimitsPolicy(Map.of(
                "request", 512,
                "response_content_limit", 1024)));

        Map<String, String> files = service.convert(svc, "ns");
        String ef = files.get("envoyfilter-content-limits.yaml");
        assertNotNull(ef);
        assertTrue(ef.contains("512"));
        String route = files.get("httproute.yaml");
        assertTrue(route.contains("response-content-limit") && route.contains("1024"));
        assertTrue(files.get("README.md").contains("WARNING"));
    }

    // ── Multi-backend path-first conversion (#28) ─────────────────────────────

    @Test
    void convert_multiBackend_distinctMounts_mapToMatchingRefs() {
        ApiService svc = basicService("Multi API", "multi-api");
        svc.authentication = auth("jwt");
        Backend orders = backend("orders", "orders", "https://orders.example.com", "/orders");
        Backend catalog = backend("catalog", "catalog", "https://catalog.example.com", "/catalog");
        svc.backends = List.of(orders, catalog);
        svc.mappingRules = List.of(
                mappingRule("GET", "/orders/list"),
                mappingRule("GET", "/catalog/items"));

        Map<String, String> files = service.convert(svc, "ns");
        String route = files.get("httproute.yaml");
        assertNotNull(route);
        assertTrue(route.contains("multi-api-orders-backend"),
                "Orders path should ref orders backend: " + route);
        assertTrue(route.contains("multi-api-catalog-backend"),
                "Catalog path should ref catalog backend: " + route);
        String policy = files.get("policy.yaml");
        assertTrue(policy.contains("name: multi-api-route")
                        || policy.contains("multi-api-route"),
                "Auth must still target the single HTTPRoute");
        assertFalse(policy.contains("orders-backend") && policy.contains("targetRef")
                        && policy.contains("catalog-backend"),
                "Auth must not split targetRefs across backends");
    }

    @Test
    void convert_multiBackend_blankPath_normalizesToRootAndCollidesWithWeights() {
        ApiService svc = basicService("Root API", "root-api");
        svc.authentication = auth("jwt");
        Backend a = backend("a", "alpha", "https://a.example.com", null);
        Backend b = backend("b", "beta", "https://b.example.com", "  ");
        a.weight = 2;
        b.weight = 3;
        svc.backends = List.of(a, b);
        svc.mappingRules = List.of(mappingRule("GET", "/anything"));

        Map<String, String> files = service.convert(svc, "ns");
        String route = files.get("httproute.yaml");
        assertTrue(route.contains("root-api-alpha-backend"));
        assertTrue(route.contains("root-api-beta-backend"));
        assertTrue(route.contains("weight: 2") || route.contains("weight: 3"),
                "Colliding mounts must emit weights: " + route);
    }

    @Test
    void convert_multiBackend_external_emitsMultiDocSeAndDrWithSystemNames() {
        ApiService svc = basicService("Ext Multi", "ext-multi");
        svc.authentication = auth("jwt");
        svc.backends = List.of(
                backend("Orders", "orders", "https://orders.example.com", "/orders"),
                backend("Pay", "pay", "https://pay.example.com", "/pay"));
        svc.mappingRules = List.of(
                mappingRule("GET", "/orders"),
                mappingRule("GET", "/pay"));

        Map<String, String> files = service.convert(svc, "ns");
        String se = files.get("serviceentry.yaml");
        String dr = files.get("destinationrule.yaml");
        assertNotNull(se);
        assertNotNull(dr);
        assertTrue(se.contains("---"), "SE must be multi-doc: " + se);
        assertTrue(dr.contains("---"), "DR must be multi-doc: " + dr);
        assertTrue(se.contains("ext-multi-orders-external"));
        assertTrue(se.contains("ext-multi-pay-external"));
        assertTrue(se.contains("ext-multi-orders-backend"));
        assertTrue(se.contains("ext-multi-pay-backend"));
        assertTrue(dr.contains("ext-multi-orders-backend-tls"));
        assertTrue(dr.contains("ext-multi-pay-backend-tls"));
    }

    @Test
    void convert_singleBackend_keepsLegacyBackendServiceName() {
        ApiService svc = basicService("Solo", "solo");
        svc.authentication = auth("jwt");
        svc.backends = List.of(backend("Only", "only", "https://only.example.com", "/"));
        svc.mappingRules = List.of(mappingRule("GET", "/api"));

        Map<String, String> files = service.convert(svc, "ns");
        String route = files.get("httproute.yaml");
        assertTrue(route.contains("solo-backend"),
                "Single-backend must keep {svc}-backend naming: " + route);
        assertFalse(route.contains("solo-only-backend"),
                "Must not include systemName in single-backend ref name");
        String se = files.get("serviceentry.yaml");
        assertTrue(se.contains("name: solo-external"));
        assertFalse(se.contains("solo-only-external"));
    }

    @Test
    void convert_overrideIgnoredWhenMultipleBackends_keepsPathRouting() {
        ApiService svc = basicService("Override Multi", "ovr-multi");
        svc.authentication = auth("jwt");
        svc.backends = List.of(
                backend("A", "a", "https://a.example.com", "/a"),
                backend("B", "b", "https://b.example.com", "/b"));
        svc.mappingRules = List.of(
                mappingRule("GET", "/a/x"),
                mappingRule("GET", "/b/y"));

        Map<String, String> files = service.convert(svc, "ns", "https://override.example.com");
        String route = files.get("httproute.yaml");
        assertTrue(route.contains("ovr-multi-a-backend"));
        assertTrue(route.contains("ovr-multi-b-backend"));
        assertFalse(route.contains("override.example.com"),
                "Override host must not collapse multi-backend routing");
        String cm = files.get("configmap.yaml");
        assertTrue(cm.toLowerCase().contains("ignored") || cm.contains("override"),
                "ConfigMap must note override ignored: " + cm);
        String readme = files.get("README.md");
        assertTrue(readme.toLowerCase().contains("ignored")
                        || readme.toLowerCase().contains("override"),
                "README must document override ignore: " + readme);
    }

    @Test
    void convert_overrideStillWinsWhenSingleBackend() {
        ApiService svc = basicService("Override Solo", "ovr-solo");
        svc.authentication = auth("jwt");
        svc.backends = List.of(backend("Only", "only", "https://only.example.com", "/"));
        svc.mappingRules = List.of(mappingRule("GET", "/api"));

        Map<String, String> files = service.convert(svc, "ns", "https://override.example.com");
        String se = files.get("serviceentry.yaml");
        assertTrue(se.contains("override.example.com"));
        String route = files.get("httproute.yaml");
        assertTrue(route.contains("URLRewrite") && route.contains("override.example.com"));
    }

    @Test
    void convert_multiBackend_doesNotSilentlyDropNonFirstBackend() {
        ApiService svc = basicService("No Drop", "no-drop");
        svc.authentication = auth("jwt");
        svc.backends = List.of(
                backend("First", "first", "https://first.example.com", "/first"),
                backend("Second", "second", "https://second.example.com", "/second"));
        svc.mappingRules = List.of(
                mappingRule("GET", "/first"),
                mappingRule("GET", "/second"));

        Map<String, String> files = service.convert(svc, "ns");
        String joined = String.join("\n", files.values());
        assertTrue(joined.contains("first.example.com"));
        assertTrue(joined.contains("second.example.com"));
        assertTrue(joined.contains("no-drop-second-backend"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── TLSPolicy (issue #21 / PR1) ───────────────────────────────────────────

    @Test
    void convert_tlsPolicyOffByDefault_noTlsPolicyFile() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", null, new ConversionOptions());
        assertFalse(files.containsKey("tlspolicy.yaml"));
        assertFalse(files.values().stream().anyMatch(y -> y.contains("kind: TLSPolicy")));
        assertFalse(files.values().stream().anyMatch(y -> y.contains("kind: Certificate")));
    }

    @Test
    void convert_tlsPolicyOn_emitsTlsPolicyWithIssuerRef() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        ConversionOptions opts = new ConversionOptions();
        opts.includeTlsPolicy = true;
        opts.tlsIssuerKind = "ClusterIssuer";
        opts.tlsIssuerName = "letsencrypt-prod";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertTrue(files.containsKey("tlspolicy.yaml"));
        String tls = files.get("tlspolicy.yaml");
        assertTrue(tls.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(tls.contains("kind: TLSPolicy"));
        assertTrue(tls.contains("name: my-api-tls-policy"));
        assertTrue(tls.contains("name: my-api-gateway"));
        assertTrue(tls.contains("kind: Gateway"));
        assertTrue(tls.contains("group: gateway.networking.k8s.io"));
        assertTrue(tls.contains("group: cert-manager.io"));
        assertTrue(tls.contains("kind: ClusterIssuer"));
        assertTrue(tls.contains("name: letsencrypt-prod"));
        assertFalse(files.values().stream().anyMatch(y -> y.contains("kind: Certificate")));

        String gw = files.get("gateway.yaml");
        assertTrue(gw.contains("name: my-api-tls"),
                "Gateway https listener must keep certificateRefs Secret {name}-tls");
    }

    @Test
    void convert_tlsPolicyOn_destinationRuleUnchanged() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        String backend = "https://api.external.example.com";

        Map<String, String> off = service.convert(svc, "ns", backend, new ConversionOptions());
        ConversionOptions onOpts = new ConversionOptions();
        onOpts.includeTlsPolicy = true;
        onOpts.tlsIssuerKind = "ClusterIssuer";
        onOpts.tlsIssuerName = "letsencrypt-prod";
        Map<String, String> on = service.convert(svc, "ns", backend, onOpts);

        assertEquals(off.get("destinationrule.yaml"), on.get("destinationrule.yaml"));
        assertTrue(on.containsKey("tlspolicy.yaml"));
        assertFalse(off.containsKey("tlspolicy.yaml"));
    }

    // ── DNSPolicy + Gateway hostname (issue #21 / PR2) ────────────────────────

    @Test
    void convert_dnsPolicyOffByDefault_noDnsArtifacts() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        Map<String, String> files = service.convert(svc, "ns", null, new ConversionOptions());
        assertFalse(files.containsKey("dnspolicy.yaml"));
        String gw = files.get("gateway.yaml");
        assertFalse(gw.contains("hostname:"),
                "Gateway listeners must omit DNS hostname when DNSPolicy is OFF");
    }

    @Test
    void convert_dnsPolicyOn_setsHostnameOnBothListenersAndEmitsDnsPolicy() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        ConversionOptions opts = new ConversionOptions();
        opts.includeDnsPolicy = true;
        opts.dnsHostname = "my-api.apps.cluster.example.com";
        opts.dnsProviderSecretName = "my-dns-secret";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        String gw = files.get("gateway.yaml");
        assertTrue(gw.contains("hostname: my-api.apps.cluster.example.com"));
        // Both http and https listeners must carry hostname
        int hostnameCount = gw.split("hostname: my-api.apps.cluster.example.com", -1).length - 1;
        assertEquals(2, hostnameCount, "hostname must appear on both http and https listeners");

        assertTrue(files.containsKey("dnspolicy.yaml"));
        String dns = files.get("dnspolicy.yaml");
        assertTrue(dns.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(dns.contains("kind: DNSPolicy"));
        assertTrue(dns.contains("name: my-api-dns-policy"));
        assertTrue(dns.contains("name: my-api-gateway"));
        assertTrue(dns.contains("providerRefs:"));
        assertTrue(dns.contains("name: my-dns-secret"));
        assertFalse(dns.toLowerCase().contains("accesskey"));
        assertFalse(dns.toLowerCase().contains("secretkey"));
        assertFalse(dns.contains("AWS_ACCESS"));
    }

    @Test
    void convert_dnsPolicyOn_omitsProviderRefsWhenSecretBlank() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        ConversionOptions opts = new ConversionOptions();
        opts.includeDnsPolicy = true;
        opts.dnsHostname = "app.apps.cluster.example.com";
        opts.dnsProviderSecretName = "  ";
        Map<String, String> files = service.convert(svc, "ns", null, opts);

        assertTrue(files.containsKey("dnspolicy.yaml"));
        String dns = files.get("dnspolicy.yaml");
        assertFalse(dns.contains("providerRefs"),
                "blank provider secret name must omit providerRefs (use cluster default-provider)");
        assertTrue(dns.contains("name: my-api-gateway"));
    }

    @Test
    void convert_dnsPolicyOn_destinationRuleUnchanged() {
        ApiService svc = basicService("my-api", "my-api");
        svc.authentication = auth("jwt");
        String backend = "https://api.external.example.com";

        Map<String, String> off = service.convert(svc, "ns", backend, new ConversionOptions());
        ConversionOptions onOpts = new ConversionOptions();
        onOpts.includeDnsPolicy = true;
        onOpts.dnsHostname = "my-api.apps.cluster.example.com";
        Map<String, String> on = service.convert(svc, "ns", backend, onOpts);

        assertEquals(off.get("destinationrule.yaml"), on.get("destinationrule.yaml"));
        assertTrue(on.containsKey("dnspolicy.yaml"));
        assertFalse(off.containsKey("dnspolicy.yaml"));
    }

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

    private Backend backend(String name, String systemName, String endpoint, String path) {
        Backend b = new Backend();
        b.name = name;
        b.systemName = systemName;
        b.privateEndpoint = endpoint;
        b.path = path;
        return b;
    }

    private MappingRule mappingRule(String method, String pattern) {
        MappingRule r = new MappingRule();
        r.httpMethod = method;
        r.pattern = pattern;
        return r;
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

    private Policy contentLimitsPolicy(Map<String, Object> configuration) {
        Policy p = new Policy();
        p.name = "content_limits";
        p.enabled = true;
        p.configuration = new HashMap<>(configuration);
        return p;
    }

    private Policy edgeLimitingPolicy(int count, int windowSeconds) {
        Policy p = new Policy();
        p.name = "edge_limiting";
        p.enabled = true;
        Map<String, Object> limiter = new HashMap<>();
        limiter.put("count", count);
        limiter.put("window", windowSeconds);
        Map<String, Object> key = new HashMap<>();
        key.put("name", "service");
        key.put("scope", "service");
        limiter.put("key", key);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("fixed_window_limiters", List.of(limiter));
        p.configuration = cfg;
        return p;
    }

    private Policy edgeLimitingLeakyBucketPolicy(int rate) {
        Policy p = new Policy();
        p.name = "edge_limiting";
        p.enabled = true;
        Map<String, Object> limiter = new HashMap<>();
        limiter.put("rate", rate);
        Map<String, Object> key = new HashMap<>();
        key.put("name", "service");
        key.put("scope", "service");
        limiter.put("key", key);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("leaky_bucket_limiters", List.of(limiter));
        p.configuration = cfg;
        return p;
    }

    private Policy edgeLimitingConnectionPolicy(int conn) {
        Policy p = new Policy();
        p.name = "edge_limiting";
        p.enabled = true;
        Map<String, Object> limiter = new HashMap<>();
        limiter.put("conn", conn);
        Map<String, Object> key = new HashMap<>();
        key.put("name", "service");
        key.put("scope", "service");
        limiter.put("key", key);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("connection_limiters", List.of(limiter));
        p.configuration = cfg;
        return p;
    }

    private Policy tokenIntrospectionPolicy(String url, String tokenTypeHint,
                                            String clientId, String clientSecret) {
        Policy p = new Policy();
        p.name = "token_introspection";
        p.enabled = true;
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("auth_type", "client_id+client_secret");
        if (url != null) {
            cfg.put("introspection_url", url);
        }
        if (tokenTypeHint != null) {
            cfg.put("token_type_hint", tokenTypeHint);
        }
        if (clientId != null) {
            cfg.put("client_id", clientId);
        }
        if (clientSecret != null) {
            cfg.put("client_secret", clientSecret);
        }
        p.configuration = cfg;
        return p;
    }

    private Map<String, Object> jwtClaimOp(String claim, String op, String value,
                                           String claimType, String valueType) {
        Map<String, Object> operation = new HashMap<>();
        operation.put("jwt_claim", claim);
        operation.put("op", op);
        operation.put("value", value);
        if (claimType != null) {
            operation.put("jwt_claim_type", claimType);
        }
        if (valueType != null) {
            operation.put("value_type", valueType);
        }
        return operation;
    }

    private Policy jwtClaimCheckPolicy(List<Map<String, Object>> operations) {
        return jwtClaimCheckPolicy(operations, "and", "/", List.of("ANY"), "plain");
    }

    private Policy jwtClaimCheckPolicy(List<Map<String, Object>> operations, String combineOp,
                                       String resource, List<String> methods, String resourceType) {
        Policy p = new Policy();
        p.name = "jwt_claim_check";
        p.enabled = true;
        Map<String, Object> rule = new HashMap<>();
        rule.put("resource", resource);
        rule.put("resource_type", resourceType);
        rule.put("methods", methods);
        rule.put("combine_op", combineOp);
        rule.put("operations", operations);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("error_message", "Invalid JWT check");
        cfg.put("rules", List.of(rule));
        p.configuration = cfg;
        return p;
    }
}
