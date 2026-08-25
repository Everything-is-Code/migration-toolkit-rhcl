package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.AuthPolicySupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        String yaml = generateOauth2IntrospectionAuthPolicy(
                builder.name(), builder.namespace(), tokenIntrospection, builder.authCacheBlock());
        if (yaml != null) {
            builder.setBaseYaml(yaml);
        }
    }

    static String generateOauth2IntrospectionAuthPolicy(String name, String namespace,
                                                        Policy policy, String authCacheBlock) {
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String endpoint = AuthPolicySupport.firstNonBlank(
                cfg.get("introspection_url"),
                cfg.get("introspectionEndpoint"),
                cfg.get("endpoint"));
        if (endpoint == null) {
            return null;
        }

        String tokenTypeHint = AuthPolicySupport.firstNonBlank(
                cfg.get("token_type_hint"),
                cfg.get("tokenTypeHint"));
        String hintBlock = tokenTypeHint != null
                ? "          tokenTypeHint: " + tokenTypeHint + "\n"
                : "";

        String secretName = name + "-oauth2-introspection";
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
    3scale-migration/auth-type: "token-introspection"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      oauth2-introspection:
        oauth2Introspection:
          endpoint: %s
%s          credentialsRef:
            name: %s
%s        credentials:
          authorizationHeader:
            prefix: Bearer
""".formatted(name, namespace, name, name, endpoint, hintBlock, secretName, authCacheBlock);
    }
}
