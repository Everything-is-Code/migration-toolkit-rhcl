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

    private String cachedYaml;
    private ConversionContext cachedCtx;

    @Override
    public String outputKey() {
        return "ratelimitpolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        String yaml = resolveYaml(ctx);
        return yaml != null && !yaml.isBlank();
    }

    @Override
    public String generate(ConversionContext ctx) {
        return resolveYaml(ctx);
    }

    private String resolveYaml(ConversionContext ctx) {
        if (cachedCtx != ctx) {
            cachedYaml = rateLimitSupport.generateRateLimitPolicy(
                    ctx.serviceKebabName, ctx.namespace, ctx.service);
            cachedCtx = ctx;
        }
        return cachedYaml;
    }

    void bindManual(RateLimitSupport support) {
        this.rateLimitSupport = support;
    }
}
