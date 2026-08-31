package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPolicyManifestTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesJwtAuthenticationShape() {
        String name = "demo-api";
        Map<String, AuthenticationRule> authentication = Map.of(
                "jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://sso.example.com/realms/demo"))));

        AuthPolicyManifest manifest = baseManifest(name, authentication, null, null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) spec.get("rules");
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) rules.get("authentication");
        @SuppressWarnings("unchecked")
        Map<String, Object> jwtAuth = (Map<String, Object>) auth.get("jwt-auth");
        @SuppressWarnings("unchecked")
        Map<String, Object> jwt = (Map<String, Object>) jwtAuth.get("jwt");
        assertEquals("https://sso.example.com/realms/demo", jwt.get("issuerUrl"));
    }

    @Test
    void emptyAuthenticationAndAuthorization_mapsSerializeAsEmptyObjects() {
        AuthPolicyManifest manifest = baseManifest(
                "demo-api",
                Map.of(),
                Map.of(),
                null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) ((Map<String, Object>) parsed.get("spec")).get("rules");
        assertNotNull(rules.get("authentication"));
        assertTrue(((Map<?, ?>) rules.get("authentication")).isEmpty());
        assertNotNull(rules.get("authorization"));
        assertTrue(((Map<?, ?>) rules.get("authorization")).isEmpty());
    }

    @Test
    void nullAuthentication_compactConstructorDefaultsToEmptyMap() {
        AuthPolicyRules rules = new AuthPolicyRules(null, Map.of(), null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(
                baseManifest("demo-api", rules.authentication(), rules.authorization(), rules.response())));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) parsed.get("spec"))
                .get("rules")).get("authentication");
        assertNotNull(auth);
        assertTrue(auth.isEmpty());
    }

    @Test
    void nullAuthenticationRuleValue_serializesAsEmptyObject() {
        AuthPolicyManifest manifest = baseManifest(
                "demo-api",
                Map.of("anonymous", new AuthenticationRule(null)),
                Map.of(),
                null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) parsed.get("spec"))
                .get("rules")).get("authentication");
        assertNotNull(auth.get("anonymous"));
        assertTrue(((Map<?, ?>) auth.get("anonymous")).isEmpty());
    }

    @Test
    void nullAuthorizationRuleValue_serializesAsEmptyObject() {
        AuthPolicyManifest manifest = baseManifest(
                "demo-api",
                Map.of(),
                Map.of("allow-all", new AuthorizationRule(null)),
                null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> authz = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) parsed.get("spec"))
                .get("rules")).get("authorization");
        assertNotNull(authz.get("allow-all"));
        assertTrue(((Map<?, ?>) authz.get("allow-all")).isEmpty());
    }

    @Test
    void authorizationRule_serializesPatternMatchingBlock() {
        Map<String, AuthorizationRule> authorization = new LinkedHashMap<>();
        authorization.put(
                "jwt-claim-check",
                new AuthorizationRule(Map.of(
                        "patternMatching",
                        Map.of(
                                "patterns",
                                List.of(Map.of(
                                        "selector", "auth.identity.sub",
                                        "operator", "eq",
                                        "value", "user-1"))))));

        AuthPolicyManifest manifest = baseManifest("demo-api", Map.of(), authorization, null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) ((Map<String, Object>) parsed.get("spec")).get("rules");
        @SuppressWarnings("unchecked")
        Map<String, Object> authz = (Map<String, Object>) rules.get("authorization");
        assertNotNull(authz.get("jwt-claim-check"));
        assertTrue(((Map<?, ?>) rules.get("authentication")).isEmpty());
    }

    @Test
    void cacheConfig_serializesInsideAuthenticationRule() {
        Map<String, Object> jwtAuthBody = new LinkedHashMap<>();
        jwtAuthBody.put("jwt", Map.of("issuerUrl", "https://sso.example.com/realms/demo"));
        jwtAuthBody.put("cache", new CacheConfig(new CacheKey("request.headers.authorization"), 300));

        AuthPolicyManifest manifest = baseManifest(
                "demo-api",
                Map.of("jwt-auth", new AuthenticationRule(jwtAuthBody)),
                Map.of(),
                null);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> jwtAuth = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) parsed.get("spec"))
                .get("rules")).get("authentication");
        @SuppressWarnings("unchecked")
        Map<String, Object> rule = (Map<String, Object>) jwtAuth.get("jwt-auth");
        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) rule.get("cache");
        assertEquals(300, ((Number) cache.get("ttl")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> key = (Map<String, Object>) cache.get("key");
        assertEquals("request.headers.authorization", key.get("selector"));
    }

    @Test
    void responseConfig_serializesAnonymousSuccessHeaders() {
        ResponseConfig response = new ResponseConfig(new ResponseSuccess(Map.of(
                "x-user-key", new HeaderEntry(new PlainValue("user-key-value")))));

        AuthPolicyManifest manifest = baseManifest("demo-api", Map.of(), Map.of(), response);

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(manifest));
        @SuppressWarnings("unchecked")
        Map<String, Object> responseNode = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) parsed.get("spec"))
                .get("rules")).get("response");
        @SuppressWarnings("unchecked")
        Map<String, Object> success = (Map<String, Object>) responseNode.get("success");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) success.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> userKey = (Map<String, Object>) headers.get("x-user-key");
        @SuppressWarnings("unchecked")
        Map<String, Object> plain = (Map<String, Object>) userKey.get("plain");
        assertEquals("user-key-value", plain.get("value"));
    }

    private static AuthPolicyManifest baseManifest(
            String name,
            Map<String, AuthenticationRule> authentication,
            Map<String, AuthorizationRule> authorization,
            ResponseConfig response) {
        return new AuthPolicyManifest(
                "kuadrant.io/v1",
                "AuthPolicy",
                new ManifestMeta(
                        name + "-auth",
                        "migration-ns",
                        Map.of("app", name, "migrated-from", "3scale"),
                        null),
                new AuthPolicySpec(
                        new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"),
                        new AuthPolicyRules(authentication, authorization, response)));
    }
}
