package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(900)
public class DestinationRuleGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "destinationrule.yaml";
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
        String trafficPolicy = b.usesTls
                ? """
  trafficPolicy:
    tls:
      mode: SIMPLE
      sni: %s
""".formatted(b.externalHost)
                : """
  trafficPolicy:
    tls:
      mode: DISABLE
""";
        return """
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: %s
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  host: %s
%s""".formatted(b.drName, namespace, appLabel, b.externalHost, trafficPolicy);
    }
}
