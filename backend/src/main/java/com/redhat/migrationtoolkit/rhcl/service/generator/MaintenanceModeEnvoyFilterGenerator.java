package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(1250)
public class MaintenanceModeEnvoyFilterGenerator implements ResourceGenerator {

    private static final String DEFAULT_STATUS = "503";
    private static final String DEFAULT_CONTENT_TYPE = "text/plain";

    @Inject
    PolicyFinder policyFinder;

    @Override
    public String outputKey() {
        return "envoyfilter-maintenance.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy policy = policyFinder.findEnabledExact(ctx.service, "maintenance_mode");
        return policy != null && isConfigEnabled(policy);
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy policy = policyFinder.findEnabledExact(ctx.service, "maintenance_mode");
        Map<String, Object> cfg = policy.configuration != null ? policy.configuration : Map.of();
        String status = resolveConfigString(cfg.get("status"), DEFAULT_STATUS);
        String message = resolveConfigString(cfg.get("message"), "");
        String contentType = resolveConfigString(cfg.get("message_content_type"), DEFAULT_CONTENT_TYPE);

        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String luaScript = """
function envoy_on_request(request_handle)
  request_handle:respond(
    {[":status"] = "%s", ["content-type"] = "%s"},
    "%s")
end
""".formatted(escapeLua(status), escapeLua(contentType), escapeLua(message));
        String indentedScript = luaScript.lines()
                .map(l -> "              " + l)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-maintenance
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: maintenance_mode
spec:
  workloadSelector:
    labels:
      gateway.networking.k8s.io/gateway-name: %s-gateway
  configPatches:
    - applyTo: HTTP_FILTER
      match:
        context: GATEWAY
        listener:
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
              subFilter:
                name: "envoy.filters.http.router"
      patch:
        operation: INSERT_BEFORE
        value:
          name: envoy.filters.http.lua
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua
            inlineCode: |
%s
""".formatted(name, namespace, name, name, indentedScript);
    }

    void bindManual(PolicyFinder finder) {
        this.policyFinder = finder;
    }

    static boolean isConfigEnabled(Policy policy) {
        if (policy == null || policy.configuration == null) {
            return false;
        }
        Object raw = policy.configuration.get("enabled");
        if (raw == null) {
            return false;
        }
        return Boolean.TRUE.equals(raw)
                || "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    static String resolveConfigString(Object raw, String defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return defaultValue;
        }
        return value;
    }

    static String escapeLua(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
