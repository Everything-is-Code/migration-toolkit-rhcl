package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Priority(310)
public class ApiKeyAuthenticationContributor implements AuthPolicyContributor {

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"apiKey".equals(authType)) {
            return;
        }
        String name = builder.name();

        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"));

        LinkedHashMap<String, Object> ruleBody = new LinkedHashMap<>();
        ruleBody.put("apiKey", Map.of("selector", Map.of("matchLabels", Map.of("app", name))));
        if (builder.cacheConfig() != null) {
            ruleBody.put("cache", builder.cacheConfig());
        }
        ruleBody.put("credentials", Map.of("authorizationHeader", Map.of("prefix", "APIKEY")));

        builder.addAuthentication("api-key-auth", new AuthenticationRule(ruleBody));
    }
}
