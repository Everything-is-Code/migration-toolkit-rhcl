package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(50)
public class AuthCachingContributor implements AuthPolicyContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        Policy caching = policyFinder().findEnabledAny(ctx.service, true, "3scale_auth_caching", "caching");
        builder.setAuthCacheBlock(buildAuthCacheBlock(caching));
    }

    static String buildAuthCacheBlock(Policy authCachingPolicy) {
        if (authCachingPolicy == null) {
            return "";
        }
        String cachingType = authCachingPolicy.configuration != null
                ? String.valueOf(authCachingPolicy.configuration.getOrDefault("caching_type", "strict"))
                : "strict";
        int ttl = switch (cachingType) {
            case "allow" -> 300;
            case "resilient" -> 600;
            default -> 60;
        };
        return """
        cache:
          key:
            selector: request.headers.authorization
          ttl: %d
""".formatted(ttl);
    }
}
