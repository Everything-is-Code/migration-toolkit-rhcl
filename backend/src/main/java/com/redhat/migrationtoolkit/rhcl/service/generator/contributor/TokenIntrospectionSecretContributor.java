package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.AuthPolicySupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(200)
public class TokenIntrospectionSecretContributor implements SecretContributor {

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
        Policy tokenIntrospection = policyFinder().findEnabled(ctx.service, "token_introspection");
        if (tokenIntrospection == null) {
            return;
        }
        Map<String, Object> cfg = tokenIntrospection.configuration != null
                ? tokenIntrospection.configuration : Map.of();
        String endpoint = AuthPolicySupport.firstNonBlank(
                cfg.get("introspection_url"),
                cfg.get("introspectionEndpoint"),
                cfg.get("endpoint"));
        if (endpoint == null) {
            return;
        }
        populateTokenIntrospectionSecret(builder, tokenIntrospection);
    }

    static String generateTokenIntrospectionSecret(String name, String namespace, Policy policy) {
        ApiService service = new ApiService();
        service.name = name;
        service.systemName = name;
        service.policies = List.of(policy);
        ConversionContext ctx = ConversionContext.build(
                service, namespace, null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        populateTokenIntrospectionSecret(builder, policy);
        return builder.build();
    }

    private static void populateTokenIntrospectionSecret(SecretBuilder builder, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String clientId = AuthPolicySupport.firstNonBlank(cfg.get("client_id"), cfg.get("clientID"));
        String clientSecret = AuthPolicySupport.firstNonBlank(cfg.get("client_secret"), cfg.get("clientSecret"));

        builder.beginOpaqueSecret(builder.name() + "-oauth2-introspection");
        builder.addLabel("auth-type", "oauth2-introspection");

        if (clientId == null || clientSecret == null) {
            builder.setYamlCommentPrefix(
                    "# WARNING: token_introspection credentials incomplete — "
                            + "fill clientID/clientSecret before apply\n");
        }

        String idValue = clientId != null ? clientId : ConversionConstants.CREDENTIAL_PLACEHOLDER;
        String secretValue = clientSecret != null ? clientSecret : ConversionConstants.CREDENTIAL_PLACEHOLDER;
        builder.addStringData("clientID", idValue);
        builder.addStringData("clientSecret", secretValue);
    }
}
