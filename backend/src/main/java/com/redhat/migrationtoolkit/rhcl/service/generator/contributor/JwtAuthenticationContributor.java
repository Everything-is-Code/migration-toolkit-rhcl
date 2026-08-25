package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(300)
public class JwtAuthenticationContributor implements AuthPolicyContributor {

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"jwt".equals(authType)) {
            return;
        }
        String issuer = ctx.service.authentication.oidcIssuerEndpoint != null
                ? ctx.service.authentication.oidcIssuerEndpoint
                : ConversionConstants.DEFAULT_OIDC_ISSUER_URL;
        String name = builder.name();
        String namespace = builder.namespace();
        builder.setBaseYaml("""
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: %s-auth
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      jwt-auth:
        jwt:
          issuerUrl: %s
%s""".formatted(name, namespace, name, name, issuer, builder.authCacheBlock()));
    }
}
