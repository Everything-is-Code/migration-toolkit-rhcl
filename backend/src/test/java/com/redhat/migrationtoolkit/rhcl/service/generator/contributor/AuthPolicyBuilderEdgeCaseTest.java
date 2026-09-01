package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheKey;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.HeaderEntry;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.PlainValue;
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

/**
 * Edge-case and unhappy-path tests for the typed AuthPolicyBuilder migration (#262 task 5).
 */
class AuthPolicyBuilderEdgeCaseTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    private static AuthPolicyBuilder newBuilder() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";
        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        return new AuthPolicyBuilder(ctx);
    }

    // 5.1 — build() with zero authentication and zero authorization should not crash
    @Test
    void build_zeroRules_producesValidManifestWithEmptyAuthentication() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.setEmptyAuthentication();

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertNotNull(yaml);
        assertTrue(yaml.contains("kind: AuthPolicy"));
        assertNull(manifest.spec().rules().authorization(), "authorization should be null when empty");
        assertNull(manifest.spec().rules().response(), "response should be null when not set");
    }

    // 5.2 — duplicate rule name last-write-wins (no silent data loss)
    @Test
    void addAuthentication_duplicateName_lastWriteWins() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://first.example.com"))));
        builder.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://second.example.com"))));

        AuthPolicyManifest manifest = builder.build();
        assertEquals(1, manifest.spec().rules().authentication().size(),
                "Duplicate names should result in single entry (last-write-wins)");
        String yaml = SERIALIZER.toYaml(manifest);
        assertTrue(yaml.contains("https://second.example.com"));
        assertFalse(yaml.contains("https://first.example.com"));
    }

    // 5.3 — merging two manifests where one has empty authentication
    @Test
    void merger_emptyAuthInOne_cleanMerge() {
        AuthPolicyBuilder builderA = newBuilder();
        builderA.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builderA.setEmptyAuthentication();

        AuthPolicyBuilder builderB = newBuilder();
        builderB.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builderB.addAuthentication("jwt-auth",
                new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://idp.example.com"))));
        builderB.addAuthorization("jwt-claim-check",
                new AuthorizationRule(Map.of("patternMatching", Map.of("patterns", java.util.List.of()))));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(builderA.build(), builderB.build());
        String yaml = SERIALIZER.toYaml(merged);

        assertNotNull(yaml);
        // After merge, jwt-auth from overlay is present
        assertTrue(yaml.contains("jwt-auth:"));
    }

    // 5.7 — description with embedded quotes, newlines, and YAML-special chars
    @Test
    void apiProductGenerator_descriptionWithSpecialChars_jacksonHandlesQuoting() {
        // Test that Jackson correctly handles special characters in description
        // (regression for the replace("\"", "'") hack removal)
        String description = "A service with \"quotes\" and : colon and # hash and --- separator";

        com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductSpec spec =
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductSpec(
                        "My API",
                        description,
                        "automatic",
                        "Published",
                        new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "my-api-route"),
                        "v1");

        com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductManifest manifest =
                new com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductManifest(
                        "devportal.kuadrant.io/v1alpha1",
                        "APIProduct",
                        new com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta(
                                "my-api", "ns", Map.of("app", "my-api"), null),
                        spec);

        String yaml = SERIALIZER.toYaml(manifest);
        // Jackson should properly quote the description without the replace hack
        assertTrue(yaml.contains("description:"), "description field should be present");
        // The double quotes in the description should be properly handled (escaped or YAML block)
        assertFalse(yaml.contains("description: A service with \"quotes\""),
                "Bare unescaped quotes are invalid YAML — Jackson should escape them");
    }

    // 5.10 — CORS header in response — verify structural YAML is correct
    @Test
    void build_withCorsResponseHeaders_serializesCorrectly() {
        AuthPolicyBuilder builder = newBuilder();
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route"));
        builder.addAuthentication("anon", new AuthenticationRule(Map.of("anonymous", Map.of())));

        Map<String, HeaderEntry> headers = new java.util.LinkedHashMap<>();
        headers.put("Access-Control-Allow-Credentials", new HeaderEntry(new PlainValue("true")));
        headers.put("Access-Control-Allow-Origin", new HeaderEntry(new PlainValue("https://example.com")));
        builder.setResponse(new ResponseConfig(new ResponseSuccess(headers)));

        AuthPolicyManifest manifest = builder.build();
        String yaml = SERIALIZER.toYaml(manifest);

        assertTrue(yaml.contains("Access-Control-Allow-Credentials:"));
        assertTrue(yaml.contains("Access-Control-Allow-Origin:"));
        assertTrue(yaml.contains("value: 'true'") || yaml.contains("value: \"true\"") || yaml.contains("value: true"));
    }
}
