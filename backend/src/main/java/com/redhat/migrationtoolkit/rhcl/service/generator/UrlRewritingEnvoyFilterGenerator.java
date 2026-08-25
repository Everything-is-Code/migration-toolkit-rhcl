package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionYamlSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1200)
public class UrlRewritingEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ConversionYamlSupport yamlSupport;

    @Override
    public String outputKey() {
        return "envoyfilter-url-rewriting.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy urlRewritingPolicy = policyFinder.findEnabledExact(ctx.service, "url_rewriting");
        if (urlRewritingPolicy == null) {
            return false;
        }
        List<Map<String, Object>> commands = yamlSupport.parseJsonObjectConfig(
                urlRewritingPolicy.configuration != null
                        ? urlRewritingPolicy.configuration.get("commands") : null);
        return !commands.isEmpty();
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy urlRewritingPolicy = policyFinder.findEnabledExact(ctx.service, "url_rewriting");
        List<Map<String, Object>> commands = yamlSupport.parseJsonObjectConfig(
                urlRewritingPolicy.configuration != null
                        ? urlRewritingPolicy.configuration.get("commands") : null);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        StringBuilder rules = new StringBuilder();
        for (Map<String, Object> cmd : commands) {
            String op = String.valueOf(cmd.getOrDefault("op", "sub"));
            String regex = String.valueOf(cmd.getOrDefault("regex", ""));
            String replace = String.valueOf(cmd.getOrDefault("replace", ""));
            if (regex.isBlank()) {
                continue;
            }
            String luaPattern = ConversionYamlSupport.pcreToLuaPattern(regex);
            String luaReplace = ConversionYamlSupport.pcreReplaceToLua(replace);
            boolean global = "gsub".equals(op);
            rules.append(String.format(
                    "  path = string.gsub(path, \"%s\", \"%s\"%s)%n",
                    luaPattern, luaReplace, global ? "" : ", 1"));
        }
        String luaScript = """
function envoy_on_request(request_handle)
  local path = request_handle:headers():get(":path")
  if path == nil then
    return
  end
%s  request_handle:headers():replace(":path", path)
end
""".formatted(rules);
        String indentedScript = luaScript.lines()
                .map(l -> "              " + l)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-url-rewriting
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: url_rewriting
    3scale-migration/note: "Auto-converted from PCRE to Lua patterns on a best-effort basis — verify before use"
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

    void bindManual(PolicyFinder finder, ConversionYamlSupport support) {
        this.policyFinder = finder;
        this.yamlSupport = support;
    }
}
