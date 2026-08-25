package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionYamlSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1100)
public class LoggingEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ConversionYamlSupport yamlSupport;

    @Override
    public String outputKey() {
        return "envoyfilter-logging.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy loggingPolicy = policyFinder.findEnabledExact(ctx.service, "logging");
        if (loggingPolicy == null) {
            return false;
        }
        List<Map<String, Object>> jsonCfg = yamlSupport.parseJsonObjectConfig(
                loggingPolicy.configuration != null
                        ? loggingPolicy.configuration.get("json_object_config") : null);
        return !jsonCfg.isEmpty();
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy loggingPolicy = policyFinder.findEnabledExact(ctx.service, "logging");
        boolean isGateway = !"workload".equals(ctx.loggingTarget);
        List<Map<String, Object>> jsonCfg = yamlSupport.parseJsonObjectConfig(
                loggingPolicy.configuration != null
                        ? loggingPolicy.configuration.get("json_object_config") : null);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        StringBuilder jsonFormat = new StringBuilder();
        for (Map<String, Object> entry : jsonCfg) {
            String key = String.valueOf(entry.getOrDefault("key", ""));
            String value = String.valueOf(entry.getOrDefault("value", ""));
            String envoyValue = ConversionYamlSupport.toEnvoyVar(value);
            jsonFormat.append(String.format("                      %s: \"%s\"%n", key, envoyValue));
        }
        String context = isGateway ? "GATEWAY" : "SIDECAR_INBOUND";
        String selectorLabel = isGateway
                ? "gateway.networking.k8s.io/gateway-name: " + name + "-gateway"
                : "app: " + name;
        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-logging-format
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: logging
spec:
  workloadSelector:
    labels:
      %s
  configPatches:
    - applyTo: NETWORK_FILTER
      match:
        context: %s
        listener:
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
      patch:
        operation: MERGE
        value:
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
            access_log:
              - name: envoy.access_loggers.stdout
                typed_config:
                  "@type": type.googleapis.com/envoy.extensions.access_loggers.stream.v3.StdoutAccessLog
                  log_format:
                    json_format:
%s""".formatted(name, namespace, name, selectorLabel, context, jsonFormat.toString());
    }

    void bindManual(PolicyFinder finder, ConversionYamlSupport support) {
        this.policyFinder = finder;
        this.yamlSupport = support;
    }
}
