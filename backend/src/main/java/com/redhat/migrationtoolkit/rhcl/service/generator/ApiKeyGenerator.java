package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(700)
public class ApiKeyGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "apikey.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        return "apiKey".equals(authType);
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        return """
apiVersion: devportal.kuadrant.io/v1alpha1
kind: APIKey
metadata:
  name: %s-api-key
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  apiProductRef:
    name: %s
  planTier: basic
  requestedBy:
    email: admin@example.com
    userId: admin
  secretRef:
    name: %s-api-key
""".formatted(name, namespace, name, name, name);
    }
}
