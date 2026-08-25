package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

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
  annotations:
    3scale-migration/auth-type: "app-id-key"
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  rules:
    authentication:
      app-id-key-auth:
        apiKey:
          selector:
            matchLabels:
              app: %s
              auth-type: app-id-key
%s        credentials:
          queryString:
            name: app_key
""".formatted(name, namespace, name, name, name, builder.authCacheBlock()));
    }
}
