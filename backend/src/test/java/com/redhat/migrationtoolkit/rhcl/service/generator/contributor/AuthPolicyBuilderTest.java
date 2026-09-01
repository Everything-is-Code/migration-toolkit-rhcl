package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheKey;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseSuccess;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyBuilderTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    private static AuthPolicyBuilder newBuilder() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        return new AuthPolicyBuilder(ctx);
    }

    @Test
    void build_assemblesAuthenticationAndAuthorizationRules() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://idp.example.com"))));
        builder.addAuthorization("jwt-claim-check",
                new AuthorizationRule(Map.of("patternMatching",
                        Map.of("patterns", java.util.List.of(
                                Map.of("selector", "auth.identity.sub", "operator", "eq", "value", "user"))))));

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertTrue(yaml.contains("name: demo-api-auth"));
        assertTrue(yaml.contains("jwt-auth:"));
        assertTrue(yaml.contains("jwt-claim-check:"));
        assertTrue(yaml.contains("auth.identity.sub"));
    }

    @Test
    void hasBase_falseBeforeAnyAuthentication() {
        AuthPolicyBuilder builder = newBuilder();
        assertFalse(builder.hasBase());
    }

    @Test
    void hasBase_trueAfterAddAuthentication() {
        AuthPolicyBuilder builder = newBuilder();
        builder.addAuthentication("anon", new AuthenticationRule(Map.of("anonymous", Map.of())));
        assertTrue(builder.hasBase());
    }

    @Test
    void hasBase_trueAfterSetEmptyAuthentication() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setEmptyAuthentication();
        assertTrue(builder.hasBase());
    }

    @Test
    void build_emptyAuthentication_serializesAsEmptyMap() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.setEmptyAuthentication();

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertTrue(yaml.contains("authentication: {}") || yaml.contains("authentication:\n"));
    }

    @Test
    void build_duplicateAuthenticationName_lastWriteWins() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://first.example.com"))));
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://second.example.com"))));

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertFalse(yaml.contains("https://first.example.com"),
                "Duplicate key should be overwritten by last write");
        assertTrue(yaml.contains("https://second.example.com"));
    }

    @Test
    void build_noAuthentication_hasBaseIsFalse() {
        AuthPolicyBuilder builder = newBuilder();
        // Do not add any authentication
        assertFalse(builder.hasBase());
    }

    @Test
    void setDiscoveryMarker_addsAnnotation() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.setEmptyAuthentication();
        builder.setDiscoveryMarker("x-discovery-marker: rhcl-test");

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertTrue(yaml.contains("x-discovery-marker: rhcl-test"));
    }

    @Test
    void build_withCacheConfig_exposedViaCacheConfig() {
        AuthPolicyBuilder builder = newBuilder();
        CacheConfig cache = new CacheConfig(new CacheKey("request.headers.authorization"), 60);
        builder.setCacheConfig(cache);

        assertEquals(cache, builder.cacheConfig());
    }

    @Test
    void build_withResponse_includesResponseSection() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.addAuthentication("anon", new AuthenticationRule(Map.of("anonymous", Map.of())));
        ResponseConfig response = new ResponseConfig(new ResponseSuccess(Map.of()));
        builder.setResponse(response);

        AuthPolicyManifest manifest = builder.build();

        assertNotNull(manifest.spec().rules().response());
    }
}
