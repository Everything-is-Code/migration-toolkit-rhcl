package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(800)
public class ServiceEntryGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "serviceentry.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.resolvedBackends.stream().anyMatch(b -> b.type == BackendType.EXTERNAL);
    }

    @Override
    public String generate(ConversionContext ctx) {
        List<ResolvedBackend> externals = ctx.resolvedBackends.stream()
                .filter(b -> b.type == BackendType.EXTERNAL)
                .toList();
        return externals.stream()
                .map(b -> generateOne(b, ctx.namespace, ctx.serviceKebabName))
                .collect(Collectors.joining("---\n"));
    }

    private static String generateOne(ResolvedBackend b, String namespace, String appLabel) {
        String portName = b.usesTls ? "https" : "http";
        String protocol = b.usesTls ? "HTTPS" : "HTTP";
        return """
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  hosts:
  - %s
  ports:
  - number: %d
    name: %s
    protocol: %s
  resolution: DNS
  location: MESH_EXTERNAL
---
apiVersion: v1
kind: Service
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  type: ExternalName
  externalName: %s
  ports:
  - name: %s
    port: %d
""".formatted(b.seName, namespace, appLabel, b.externalHost, b.port, portName, protocol,
                b.refName, namespace, appLabel, b.externalHost, portName, b.port);
    }
}
