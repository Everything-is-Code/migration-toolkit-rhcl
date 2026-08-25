package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

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
      api-key-auth:
        apiKey:
          selector:
            matchLabels:
              app: %s
%s        credentials:
          authorizationHeader:
            prefix: APIKEY
""".formatted(name, namespace, name, name, name, builder.authCacheBlock()));
    }
}
