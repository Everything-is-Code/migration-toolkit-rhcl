package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(600)
public class ApiProductGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "apiproduct.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String displayName = ctx.service.name != null ? ctx.service.name : name;
        String description = ctx.service.description != null ? ctx.service.description : "Migrated from 3scale";
        return """
apiVersion: devportal.kuadrant.io/v1alpha1
kind: APIProduct
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  displayName: "%s"
  description: "%s"
  approvalMode: automatic
  publishStatus: Published
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: %s-route
  version: v1
""".formatted(name, namespace, name, displayName, description.replace("\"", "'"), name);
    }
}
