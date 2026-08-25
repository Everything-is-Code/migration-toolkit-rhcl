package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(100)
public class GatewayGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "gateway.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return !RegistryDiscoveryMarkers.isDiscoveryService(ctx);
    }

    @Override
    public String generate(ConversionContext ctx) {
        String hostname = ctx.emitDnsPolicy() ? ctx.options.dnsHostname.trim() : null;
        String hostnameLine = (hostname != null && !hostname.isBlank())
                ? "\n      hostname: " + hostname.trim()
                : "";
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        return """
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: %s-gateway
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
spec:
  gatewayClassName: istio
  listeners:
    - name: http
      protocol: HTTP
      port: %d%s
      allowedRoutes:
        namespaces:
          from: Same
    - name: https
      protocol: HTTPS
      port: %d%s
      tls:
        mode: Terminate
        certificateRefs:
          - name: %s-tls
      allowedRoutes:
        namespaces:
          from: Same
""".formatted(name, namespace, name,
                ConversionConstants.DEFAULT_HTTP_PORT, hostnameLine,
                ConversionConstants.DEFAULT_HTTPS_PORT, hostnameLine,
                name);
    }
}
