package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyRules;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges two typed {@link AuthPolicyManifest} instances by combining their named-rule maps.
 * Rules from the overlay take precedence over base rules with the same name (last-write-wins).
 * Metadata (name, namespace, labels, annotations) is taken from the base manifest.
 * <p>
 * Production {@link AuthPolicyGenerator} accumulates rules in a single {@link AuthPolicyBuilder};
 * this merger is retained for tested merge semantics and future multi-manifest composition.
 */
public final class AuthPolicyYamlMerger {

    private AuthPolicyYamlMerger() {
    }

    /**
     * Merge all authentication and authorization rules from {@code base} and {@code overlay}
     * into a single manifest. The base manifest's metadata and target ref are preserved.
     * Rules in the overlay with the same name replace base rules.
     */
    public static AuthPolicyManifest merge(AuthPolicyManifest base, AuthPolicyManifest overlay) {
        if (base == null) {
            return overlay;
        }
        if (overlay == null) {
            return base;
        }

        Map<String, AuthenticationRule> mergedAuth = mergeRules(
                base.spec() != null && base.spec().rules() != null ? base.spec().rules().authentication() : null,
                overlay.spec() != null && overlay.spec().rules() != null ? overlay.spec().rules().authentication() : null);

        Map<String, AuthorizationRule> mergedAuthz = mergeRules(
                base.spec() != null && base.spec().rules() != null ? base.spec().rules().authorization() : null,
                overlay.spec() != null && overlay.spec().rules() != null ? overlay.spec().rules().authorization() : null);

        ResponseConfig mergedResponse = overlay.spec() != null && overlay.spec().rules() != null
                && overlay.spec().rules().response() != null
                ? overlay.spec().rules().response()
                : (base.spec() != null && base.spec().rules() != null ? base.spec().rules().response() : null);

        AuthPolicyRules rules = new AuthPolicyRules(
                mergedAuth == null ? Map.of() : mergedAuth,
                mergedAuthz,
                mergedResponse);

        AuthPolicySpec spec = new AuthPolicySpec(
                base.spec() != null ? base.spec().targetRef() : null,
                rules);

        return new AuthPolicyManifest(base.apiVersion(), base.kind(), base.metadata(), spec);
    }

    private static <V> Map<String, V> mergeRules(Map<String, V> base, Map<String, V> overlay) {
        if (base == null && overlay == null) {
            return null;
        }
        LinkedHashMap<String, V> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overlay != null) {
            merged.putAll(overlay);
        }
        return merged.isEmpty() ? null : merged;
    }
}
