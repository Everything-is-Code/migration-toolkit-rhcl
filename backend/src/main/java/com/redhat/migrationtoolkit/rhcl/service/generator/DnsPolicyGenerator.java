package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(1800)
public class DnsPolicyGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "dnspolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.emitDnsPolicy();
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String providerSecretName = ctx.options.dnsProviderSecretName;
        String providerBlock = "";
        if (providerSecretName != null && !providerSecretName.isBlank()) {
            providerBlock = """
  providerRefs:
    - name: %s
""".formatted(providerSecretName.trim());
        }
        return """
apiVersion: kuadrant.io/v1
kind: DNSPolicy
metadata:
  name: %s-dns-policy
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: %s-gateway
%s""".formatted(name, namespace, name, name, providerBlock);
    }
}
