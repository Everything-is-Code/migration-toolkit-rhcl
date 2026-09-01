package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.AuthPolicySupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Priority(200)
public class Oauth2IntrospectionContributor implements AuthPolicyContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        Policy tokenIntrospection = policyFinder().findEnabled(ctx.service, "token_introspection");
        if (tokenIntrospection == null) {
            return;
        }
        boolean contributed = contributeOauth2Introspection(builder, tokenIntrospection);
        if (!contributed) {
            return;
        }
        builder.addAnnotation("3scale-migration/auth-type", "token-introspection");
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute",
                builder.name() + "-route"));
    }

    static boolean contributeOauth2Introspection(AuthPolicyBuilder builder, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String endpoint = AuthPolicySupport.firstNonBlank(
                cfg.get("introspection_url"),
                cfg.get("introspectionEndpoint"),
                cfg.get("endpoint"));
        if (endpoint == null) {
            return false;
        }

        String tokenTypeHint = AuthPolicySupport.firstNonBlank(
                cfg.get("token_type_hint"),
                cfg.get("tokenTypeHint"));

        String secretName = builder.name() + "-oauth2-introspection";

        LinkedHashMap<String, Object> introspection = new LinkedHashMap<>();
        introspection.put("endpoint", endpoint);
        if (tokenTypeHint != null) {
            introspection.put("tokenTypeHint", tokenTypeHint);
        }
        introspection.put("credentialsRef", Map.of("name", secretName));

        LinkedHashMap<String, Object> ruleBody = new LinkedHashMap<>();
        ruleBody.put("oauth2Introspection", introspection);
        if (builder.cacheConfig() != null) {
            ruleBody.put("cache", builder.cacheConfig());
        }
        ruleBody.put("credentials", Map.of("authorizationHeader", Map.of("prefix", "Bearer")));

        builder.addAuthentication("oauth2-introspection", new AuthenticationRule(ruleBody));
        return true;
    }
}
