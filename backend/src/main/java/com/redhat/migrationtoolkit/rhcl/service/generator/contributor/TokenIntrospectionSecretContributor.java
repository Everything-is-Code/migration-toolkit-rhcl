package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.AuthPolicySupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        builder.setSecretYaml(generateTokenIntrospectionSecret(
                builder.name(), builder.namespace(), tokenIntrospection));
    }

    static String generateTokenIntrospectionSecret(String name, String namespace, Policy policy) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String clientId = AuthPolicySupport.firstNonBlank(cfg.get("client_id"), cfg.get("clientID"));
        String clientSecret = AuthPolicySupport.firstNonBlank(cfg.get("client_secret"), cfg.get("clientSecret"));

        String warning = "";
        if (clientId == null || clientSecret == null) {
            warning = "# WARNING: token_introspection credentials incomplete — "
                    + "fill clientID/clientSecret before apply\n";
        }

        String idValue = clientId != null ? clientId : ConversionConstants.CREDENTIAL_PLACEHOLDER;
        String secretValue = clientSecret != null ? clientSecret : ConversionConstants.CREDENTIAL_PLACEHOLDER;

        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-oauth2-introspection
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
    auth-type: oauth2-introspection
type: Opaque
%sstringData:
  clientID: "%s"
  clientSecret: "%s"
""".formatted(name, namespace, name, warning, idValue, secretValue);
    }
}
