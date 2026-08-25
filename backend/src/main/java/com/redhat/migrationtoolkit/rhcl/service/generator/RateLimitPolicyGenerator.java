package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(1600)
public class RateLimitPolicyGenerator implements ResourceGenerator {

    @Inject
    RateLimitSupport rateLimitSupport;

    @Override
    public String outputKey() {
        return "ratelimitpolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        boolean hasEdgeLimiting = ctx.service.policies != null
                && ctx.service.policies.stream().anyMatch(p ->
                        p.enabled && "edge_limiting".equals(p.name));
        boolean hasPlansWithLimits = ctx.service.applicationPlans != null
                && ctx.service.applicationPlans.stream().anyMatch(plan ->
                        plan.limits != null && !plan.limits.isEmpty());
        return hasEdgeLimiting || hasPlansWithLimits;
    }

    @Override
    public String generate(ConversionContext ctx) {
        return rateLimitSupport.generateRateLimitPolicy(
                ctx.serviceKebabName, ctx.namespace, ctx.service);
    }

    void bindManual(RateLimitSupport support) {
        this.rateLimitSupport = support;
    }
}
