package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(1000)
public class TelemetryGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Override
    public String outputKey() {
        return "telemetry.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return policyFinder.findEnabledExact(ctx.service, "logging") != null;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy loggingPolicy = policyFinder.findEnabledExact(ctx.service, "logging");
        boolean isGateway = !"workload".equals(ctx.loggingTarget);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        Map<String, Object> cfg = loggingPolicy.configuration != null ? loggingPolicy.configuration : Map.of();
        boolean enableJson = Boolean.TRUE.equals(cfg.get("enable_json_logs"));
        boolean enableAccess = !Boolean.FALSE.equals(cfg.get("enable_access_logs"));
        String selectorLabel = isGateway
                ? "gateway.networking.k8s.io/gateway-name: " + name + "-gateway"
                : "app: " + name;
        return """
apiVersion: telemetry.istio.io/v1
kind: Telemetry
metadata:
  name: %s-logging
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: logging
    3scale-migration/enable-json: "%s"
    3scale-migration/enable-access: "%s"
spec:
  selector:
    matchLabels:
      %s
  accessLogging:
    - providers:
        - name: envoy
""".formatted(name, namespace, name, enableJson, enableAccess, selectorLabel);
    }

    void bindManual(PolicyFinder finder) {
        this.policyFinder = finder;
    }
}
