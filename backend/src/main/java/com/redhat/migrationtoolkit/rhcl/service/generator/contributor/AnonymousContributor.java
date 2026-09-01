package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthenticationRule;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.HeaderEntry;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.PlainValue;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseConfig;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ResponseSuccess;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Priority(100)
public class AnonymousContributor implements AuthPolicyContributor {

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
        Policy anonymousPolicy = policyFinder().findEnabledAny(
                ctx.service, false, "default_credentials", "anonymous_access");
        if (anonymousPolicy == null) {
            return;
        }
        contributeAnonymousAuth(builder, anonymousPolicy, ctx.anonymousTarget);
    }

    static void contributeAnonymousAuth(AuthPolicyBuilder builder, Policy policy, String anonymousTarget) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String authType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));

        boolean targetGateway = "gateway".equals(anonymousTarget);
        String targetKind = targetGateway ? "Gateway" : "HTTPRoute";
        String targetName = targetGateway ? builder.name() + "-gateway" : builder.name() + "-route";

        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", targetKind, targetName));
        builder.addAnnotation("3scale-migration/anonymous-access", "true");
        builder.addAnnotation("3scale-migration/auth-type", authType);

        builder.addAuthentication("anonymous", new AuthenticationRule(Map.of("anonymous", Map.of())));

        ResponseConfig responseConfig = buildResponseConfig(authType, cfg);
        if (responseConfig != null) {
            builder.setResponse(responseConfig);
        }
    }

    private static ResponseConfig buildResponseConfig(String authType, Map<String, Object> cfg) {
        Map<String, HeaderEntry> headers = new LinkedHashMap<>();
        if ("user_key".equals(authType)) {
            String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            headers.put("x-user-key", new HeaderEntry(new PlainValue(userKey)));
        } else if ("app_id_and_app_key".equals(authType) || "app_id".equals(authType)) {
            String appId = String.valueOf(cfg.getOrDefault("app_id", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            headers.put("x-app-id", new HeaderEntry(new PlainValue(appId)));
            headers.put("x-app-key", new HeaderEntry(new PlainValue(appKey)));
        }
        if (headers.isEmpty()) {
            return null;
        }
        return new ResponseConfig(new ResponseSuccess(headers));
    }
}
