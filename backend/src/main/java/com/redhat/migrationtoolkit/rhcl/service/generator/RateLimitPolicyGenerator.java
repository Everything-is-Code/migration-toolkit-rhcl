package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RateLimitPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(1600)
public class RateLimitPolicyGenerator implements ResourceGenerator {

    @Inject
    RateLimitSupport rateLimitSupport;

    @Inject
    ManifestSerializer manifestSerializer;

    @Override
    public String outputKey() {
        return "ratelimitpolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        boolean hasEdgeLimiting = ctx.service.policies != null
                && ctx.service.policies.stream().anyMatch(p ->
                        Boolean.TRUE.equals(p.enabled)
                                && p.name != null
                                && "edge_limiting".equalsIgnoreCase(p.name));
        boolean hasPlansWithLimits = ctx.service.applicationPlans != null
                && ctx.service.applicationPlans.stream().anyMatch(plan ->
                        plan.limits != null && !plan.limits.isEmpty());
        return hasEdgeLimiting || hasPlansWithLimits;
    }

    @Override
    public String generate(ConversionContext ctx) {
        RateLimitPolicyManifest manifest = rateLimitSupport.buildManifest(
                ctx.serviceKebabName, ctx.namespace, ctx.service);
        if (manifest == null) {
            return null;
        }
        return serializer().toYaml(manifest);
    }

    void bindManual(RateLimitSupport support) {
        this.rateLimitSupport = support;
    }

    void bindManualSerializer(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
