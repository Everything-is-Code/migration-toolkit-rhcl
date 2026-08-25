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
public class AnonymousSecretContributor implements SecretContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (builder.hasSecret()) {
            return;
        }
        Policy anonymousPolicy = policyFinder().findEnabledAny(
                ctx.service, false, "default_credentials", "anonymous_access");
        if (anonymousPolicy == null) {
            return;
        }
        builder.setSecretYaml(buildAnonymousSecret(builder.name(), builder.namespace(), anonymousPolicy));
    }

    static String buildAnonymousSecret(String name, String namespace, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String polAuthType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));
        StringBuilder stringData = new StringBuilder();
        if ("user_key".equals(polAuthType)) {
            String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            stringData.append(String.format("  user_key: \"%s\"%n", userKey));
        } else {
            String appId = String.valueOf(cfg.getOrDefault("app_id", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            stringData.append(String.format("  app_id: \"%s\"%n", appId));
            stringData.append(String.format("  app_key: \"%s\"%n", appKey));
        }
        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-anonymous-credentials
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
type: Opaque
stringData:
%s""".formatted(name, namespace, name, stringData);
    }
}
