package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Priority(300)
public class JwtAuthenticationContributor implements AuthPolicyContributor {

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"jwt".equals(authType)) {
            return;
        }
        String issuer = ctx.service.authentication.oidcIssuerEndpoint != null
                ? ctx.service.authentication.oidcIssuerEndpoint
                : ConversionConstants.DEFAULT_OIDC_ISSUER_URL;

        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute",
                builder.name() + "-route"));

        LinkedHashMap<String, Object> ruleBody = new LinkedHashMap<>();
        ruleBody.put("jwt", Map.of("issuerUrl", issuer));
        if (builder.cacheConfig() != null) {
            ruleBody.put("cache", builder.cacheConfig());
        }
        builder.addAuthentication("jwt-auth", new AuthenticationRule(ruleBody));
    }
}
