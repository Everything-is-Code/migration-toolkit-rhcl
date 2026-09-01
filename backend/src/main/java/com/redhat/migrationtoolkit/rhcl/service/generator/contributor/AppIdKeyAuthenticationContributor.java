package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Priority(320)
public class AppIdKeyAuthenticationContributor implements AuthPolicyContributor {

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"appIdKey".equals(authType)) {
            return;
        }
        String name = builder.name();

        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route"));
        builder.addAnnotation("3scale-migration/auth-type", "app-id-key");

        LinkedHashMap<String, Object> ruleBody = new LinkedHashMap<>();
        ruleBody.put("apiKey", Map.of("selector",
                Map.of("matchLabels", Map.of("app", name, "auth-type", "app-id-key"))));
        if (builder.cacheConfig() != null) {
            ruleBody.put("cache", builder.cacheConfig());
        }
        ruleBody.put("credentials", Map.of("queryString", Map.of("name", "app_key")));

        builder.addAuthentication("app-id-key-auth", new AuthenticationRule(ruleBody));
    }
}
