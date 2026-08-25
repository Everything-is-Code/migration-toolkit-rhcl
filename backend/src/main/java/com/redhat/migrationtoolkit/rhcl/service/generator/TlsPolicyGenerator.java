package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(1700)
public class TlsPolicyGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "tlspolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.options.includeTlsPolicy;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String issuerKind = ctx.options.tlsIssuerKind;
        String issuerName = ctx.options.tlsIssuerName;
        String kind = (issuerKind != null && !issuerKind.isBlank()) ? issuerKind : "ClusterIssuer";
        String issuer = (issuerName != null && !issuerName.isBlank()) ? issuerName : "letsencrypt-prod";
        return """
apiVersion: kuadrant.io/v1
kind: TLSPolicy
metadata:
  name: %s-tls-policy
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: Gateway
    name: %s-gateway
  issuerRef:
    group: cert-manager.io
    kind: %s
    name: %s
""".formatted(name, namespace, name, name, kind, issuer);
    }
}
