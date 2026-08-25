package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        builder.setBaseYaml(generateAnonymousAuthPolicy(
                builder.name(), builder.namespace(), anonymousPolicy, ctx.anonymousTarget));
    }

    static String generateAnonymousAuthPolicy(String name, String namespace, Policy policy,
                                              String anonymousTarget) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String authType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));

        StringBuilder responseHeaders = new StringBuilder();
        if ("user_key".equals(authType)) {
            String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            responseHeaders.append(String.format(
                    "          x-user-key:%n            plain:%n              value: \"%s\"%n", userKey));
        } else if ("app_id_and_app_key".equals(authType) || "app_id".equals(authType)) {
            String appId = String.valueOf(cfg.getOrDefault("app_id", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            responseHeaders.append(String.format(
                    "          x-app-id:%n            plain:%n              value: \"%s\"%n", appId));
            responseHeaders.append(String.format(
                    "          x-app-key:%n            plain:%n              value: \"%s\"%n", appKey));
        }

        String responseSection = responseHeaders.length() > 0
                ? "    response:\n      success:\n        headers:\n" + responseHeaders
                : "";

        boolean targetGateway = "gateway".equals(anonymousTarget);
        String targetKind = targetGateway ? "Gateway" : "HTTPRoute";
        String targetName = targetGateway ? name + "-gateway" : name + "-route";

        return """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/anonymous-access: "true"
    3scale-migration/auth-type: "%s"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: %s
    name: %s
  rules:
    authentication:
      anonymous:
        anonymous: {}
%s""".formatted(name, namespace, name, authType, targetKind, targetName, responseSection);
    }
}
