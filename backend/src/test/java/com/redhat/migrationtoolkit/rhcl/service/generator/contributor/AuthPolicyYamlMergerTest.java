package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyRules;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.HeaderEntry;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.PlainValue;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseSuccess;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyYamlMergerTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    private static AuthPolicyManifest baseManifest() {
        ManifestMeta meta = new ManifestMeta("demo-api-auth", "ns",
                Map.of("app", "demo-api"), null);
        TargetRef ref = new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route");
        AuthPolicyRules rules = new AuthPolicyRules(
                Map.of("jwt-auth", new AuthenticationRule(Map.of("jwt", Map.of("issuerUrl", "https://idp.example.com")))),
                null,
                null);
        return new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy", meta,
                new AuthPolicySpec(ref, rules));
    }

    @Test
    void merge_addsAuthorizationRuleFromOverlay() {
        AuthPolicyManifest base = baseManifest();

        AuthPolicyRules overlayRules = new AuthPolicyRules(
                Map.of(),
                Map.of("jwt-claim-check", new AuthorizationRule(
                        Map.of("patternMatching", Map.of("patterns",
                                java.util.List.of(Map.of("selector", "auth.identity.sub",
                                        "operator", "eq", "value", "alice")))))),
                null);
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy",
                base.metadata(), new AuthPolicySpec(base.spec().targetRef(), overlayRules));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);
        String yaml = SERIALIZER.toYaml(merged);

        assertTrue(yaml.contains("jwt-auth:"));
        assertTrue(yaml.contains("jwt-claim-check:"));
        assertTrue(yaml.contains("auth.identity.sub"));
    }

    @Test
    void merge_overlayAuthenticationOverridesBaseForSameKey() {
        AuthPolicyManifest base = baseManifest();

        AuthPolicyRules overlayRules = new AuthPolicyRules(
                Map.of("jwt-auth", new AuthenticationRule(Map.of("jwt",
                        Map.of("issuerUrl", "https://other-idp.example.com")))),
                null, null);
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy",
                base.metadata(), new AuthPolicySpec(base.spec().targetRef(), overlayRules));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);
        String yaml = SERIALIZER.toYaml(merged);

        assertTrue(yaml.contains("https://other-idp.example.com"), "Overlay should win for same key");
        assertFalse(yaml.contains("https://idp.example.com"), "Base value should be replaced");
    }

    @Test
    void merge_nullBase_returnsOverlay() {
        AuthPolicyManifest overlay = baseManifest();
        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(null, overlay);
        assertSame(overlay, merged);
    }

    @Test
    void merge_nullOverlay_returnsBase() {
        AuthPolicyManifest base = baseManifest();
        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, null);
        assertSame(base, merged);
    }

    @Test
    void merge_emptyAuthenticationInOverlay_doesNotEraseBaseAuthentication() {
        AuthPolicyManifest base = baseManifest();
        AuthPolicyRules overlayRules = new AuthPolicyRules(Map.of(), null, null);
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy",
                base.metadata(), new AuthPolicySpec(base.spec().targetRef(), overlayRules));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);

        assertNotNull(merged.spec().rules().authentication());
        assertTrue(merged.spec().rules().authentication().containsKey("jwt-auth"),
                "Base authentication rules should be preserved when overlay is empty");
    }

    @Test
    void merge_preservesBaseMetadata() {
        AuthPolicyManifest base = baseManifest();
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy",
                new ManifestMeta("other-name", "other-ns", Map.of(), null),
                base.spec());

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);

        assertEquals("demo-api-auth", merged.metadata().name());
        assertEquals("ns", merged.metadata().namespace());
    }

    @Test
    void merge_nullBaseSpec_usesOverlayRules() {
        ManifestMeta meta = new ManifestMeta("demo-api-auth", "ns", Map.of("app", "demo-api"), null);
        AuthPolicyManifest base = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy", meta, null);

        AuthPolicyRules overlayRules = new AuthPolicyRules(
                Map.of("api-key", new AuthenticationRule(Map.of("apiKey", Map.of()))),
                null,
                null);
        TargetRef ref = new TargetRef("gateway.networking.k8s.io", "HTTPRoute", "demo-api-route");
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy", meta,
                new AuthPolicySpec(ref, overlayRules));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);

        assertNotNull(merged.spec().rules().authentication());
        assertTrue(merged.spec().rules().authentication().containsKey("api-key"));
    }

    @Test
    void merge_overlayResponseOverridesBase() {
        AuthPolicyManifest base = baseManifest();
        ResponseConfig baseResponse = new ResponseConfig(new ResponseSuccess(Map.of()));
        AuthPolicyRules baseRules = new AuthPolicyRules(
                base.spec().rules().authentication(),
                null,
                baseResponse);
        base = new AuthPolicyManifest(base.apiVersion(), base.kind(), base.metadata(),
                new AuthPolicySpec(base.spec().targetRef(), baseRules));

        ResponseConfig overlayResponse = new ResponseConfig(new ResponseSuccess(Map.of(
                "X-Overlay", new HeaderEntry(new PlainValue("yes")))));
        AuthPolicyRules overlayRules = new AuthPolicyRules(Map.of(), null, overlayResponse);
        AuthPolicyManifest overlay = new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy",
                base.metadata(), new AuthPolicySpec(base.spec().targetRef(), overlayRules));

        AuthPolicyManifest merged = AuthPolicyYamlMerger.merge(base, overlay);

        assertNotNull(merged.spec().rules().response());
        assertNotNull(merged.spec().rules().response().success());
        assertTrue(merged.spec().rules().response().success().headers().containsKey("X-Overlay"));
    }
}
