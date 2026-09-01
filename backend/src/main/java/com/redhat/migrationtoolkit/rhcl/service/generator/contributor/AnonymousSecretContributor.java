package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
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
        populateAnonymousSecret(builder, anonymousPolicy);
    }

    static String buildAnonymousSecret(String name, String namespace, Policy policy) {
        ApiService service = new ApiService();
        service.name = name;
        service.systemName = name;
        service.policies = List.of(policy);
        ConversionContext ctx = ConversionContext.build(
                service, namespace, null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        populateAnonymousSecret(builder, policy);
        return builder.build();
    }

    private static void populateAnonymousSecret(SecretBuilder builder, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String polAuthType = String.valueOf(cfg.getOrDefault("auth_type", "user_key"));
        builder.beginOpaqueSecret(builder.name() + "-anonymous-credentials");
        if ("user_key".equals(polAuthType)) {
            String userKey = String.valueOf(cfg.getOrDefault("user_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            builder.addStringData("user_key", userKey);
        } else {
            String appId = String.valueOf(cfg.getOrDefault("app_id", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            String appKey = String.valueOf(cfg.getOrDefault("app_key", ConversionConstants.CREDENTIAL_PLACEHOLDER));
            builder.addStringData("app_id", appId);
            builder.addStringData("app_key", appKey);
        }
    }
}
