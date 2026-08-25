package com.redhat.migrationtoolkit.rhcl.service.generator.discovery;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.ResourceGenerator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test-only generator proving CDI auto-discovery of {@link ResourceGenerator} beans.
 */
@ApplicationScoped
@Priority(50)
public class TestMarkerGatewayGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "gateway.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return RegistryDiscoveryMarkers.isDiscoveryService(ctx);
    }

    @Override
    public String generate(ConversionContext ctx) {
        return """
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: %s-gateway
  namespace: %s
  annotations:
    %s
spec:
  gatewayClassName: istio
  listeners: []
""".formatted(ctx.serviceKebabName, ctx.namespace, RegistryDiscoveryMarkers.MARKER);
    }
}
