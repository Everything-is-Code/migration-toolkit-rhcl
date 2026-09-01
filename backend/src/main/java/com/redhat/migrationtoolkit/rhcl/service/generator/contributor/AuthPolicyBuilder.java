package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyRules;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.KuadrantManifestSupport;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed accumulator for AuthPolicy assembled by contributors.
 * <p>
 * Contributors call {@link #addAuthentication}, {@link #addAuthorization}, and
 * {@link #setResponse} to populate the policy; {@link #build()} produces an immutable
 * {@link AuthPolicyManifest} record ready for serialization via {@code ManifestSerializer}.
 * <p>
 * The auth-caching contributor sets a {@link CacheConfig} via {@link #setCacheConfig} before
 * authentication contributors run; auth contributors embed the cache into their rule via
 * {@link #cacheConfig()}.
 */
public final class AuthPolicyBuilder {

    private static final Logger LOG = Logger.getLogger(AuthPolicyBuilder.class);

    private final String name;
    private final String namespace;
    private final boolean includeMigratedFromLabel;

    private final LinkedHashMap<String, AuthenticationRule> authentication = new LinkedHashMap<>();
    private final LinkedHashMap<String, AuthorizationRule> authorization = new LinkedHashMap<>();
    private ResponseConfig response;
    private TargetRef targetRef;
    private CacheConfig cacheConfig;
    private boolean authInitialized;

    private final LinkedHashMap<String, String> extraAnnotations = new LinkedHashMap<>();

    public AuthPolicyBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
        this.includeMigratedFromLabel = ctx.includeMigratedFromLabel;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    /** True once an authentication rule (or the explicit-empty flag) has been set by a contributor. */
    public boolean hasBase() {
        return authInitialized;
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Add a named authentication rule (e.g. {@code "jwt-auth"}).
     * Last write wins when the same name is used twice.
     */
    public AuthPolicyBuilder addAuthentication(String ruleName, AuthenticationRule rule) {
        if (authentication.containsKey(ruleName)) {
            LOG.debugf("Overwriting authentication rule '%s' (last-write-wins)", ruleName);
        }
        authentication.put(ruleName, rule);
        authInitialized = true;
        return this;
    }

    /**
     * Mark authentication as explicitly empty ({@code authentication: {}}).
     * Used by {@code EmptyAuthenticationContributor} when no other contributor sets a rule.
     */
    public AuthPolicyBuilder setEmptyAuthentication() {
        authInitialized = true;
        return this;
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    /**
     * Add a named authorization rule (e.g. {@code "ip-check"}).
     * Last write wins when the same name is used twice.
     */
    public AuthPolicyBuilder addAuthorization(String ruleName, AuthorizationRule rule) {
        if (authorization.containsKey(ruleName)) {
            LOG.debugf("Overwriting authorization rule '%s' (last-write-wins)", ruleName);
        }
        authorization.put(ruleName, rule);
        return this;
    }

    // ── Response ──────────────────────────────────────────────────────────────

    public AuthPolicyBuilder setResponse(ResponseConfig response) {
        this.response = response;
        return this;
    }

    // ── Target ref ────────────────────────────────────────────────────────────

    public AuthPolicyBuilder setTargetRef(TargetRef ref) {
        this.targetRef = ref;
        return this;
    }

    // ── Labels / Annotations ─────────────────────────────────────────────────

    public AuthPolicyBuilder addAnnotation(String key, String value) {
        extraAnnotations.put(key, value);
        return this;
    }

    // ── Auth caching ─────────────────────────────────────────────────────────

    public AuthPolicyBuilder setCacheConfig(CacheConfig cache) {
        this.cacheConfig = cache;
        return this;
    }

    /** Returns the auth-caching config set by {@code AuthCachingContributor}, or {@code null}. */
    public CacheConfig cacheConfig() {
        return cacheConfig;
    }

    // ── Discovery marker (test infra) ─────────────────────────────────────────

    /**
     * Parses a {@code "key: value"} discovery marker string into a metadata annotation.
     * Used by test discovery contributors only — production code should call
     * {@link #addAnnotation} directly.
     */
    public void setDiscoveryMarker(String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        int colon = marker.indexOf(':');
        if (colon <= 0) {
            LOG.warnf("Ignoring malformed discovery marker (expected key: value): %s", marker);
            return;
        }
        String key = marker.substring(0, colon).trim();
        String value = marker.substring(colon + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            LOG.warnf("Ignoring malformed discovery marker (empty key or value): %s", marker);
            return;
        }
        extraAnnotations.put(key, value);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Produce an immutable {@link AuthPolicyManifest} from accumulated rules.
     * Returns a manifest with empty authentication ({@code authentication: {}}) when
     * {@link #setEmptyAuthentication()} was called and no rules were added.
     */
    public AuthPolicyManifest build() {
        Map<String, String> annotations = extraAnnotations.isEmpty() ? null : new LinkedHashMap<>(extraAnnotations);
        ManifestMeta meta = new ManifestMeta(
                name + "-auth",
                namespace,
                KuadrantManifestSupport.baseLabels(name, includeMigratedFromLabel),
                annotations);

        Map<String, AuthenticationRule> authMap = authentication.isEmpty() ? Map.of() : new LinkedHashMap<>(authentication);
        Map<String, AuthorizationRule> authzMap = authorization.isEmpty() ? null : new LinkedHashMap<>(authorization);

        AuthPolicyRules rules = new AuthPolicyRules(authMap, authzMap, response);
        AuthPolicySpec spec = new AuthPolicySpec(targetRef, rules);

        return new AuthPolicyManifest("kuadrant.io/v1", "AuthPolicy", meta, spec);
    }
}
