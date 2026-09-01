package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionYamlSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.EnvoyFilterManifests;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1200)
public class UrlRewritingEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ConversionYamlSupport yamlSupport;

    @Inject
    ManifestSerializer manifestSerializer;

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
        String luaScript = buildLuaScript(commands);

        Map<String, Object> patchValue = Map.of(
                "name", "envoy.filters.http.lua",
                "typed_config", Map.of(
                        "@type", "type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua",
                        "inlineCode", luaScript));

        Map<String, Object> document = EnvoyFilterManifests.baseDocument(
                name, namespace, name + "-url-rewriting", ctx.includeMigratedFromLabel);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("3scale-migration/source", "url_rewriting");
        annotations.put(
                "3scale-migration/note",
                "Auto-converted from PCRE to Lua patterns on a best-effort basis — verify before use");
        metadata.put("annotations", annotations);

        Map<String, Object> spec = EnvoyFilterManifests.gatewayWorkloadSpec(name);
        EnvoyFilterManifests.withConfigPatches(
                spec, List.of(EnvoyFilterManifests.httpFilterGatewayPatch("INSERT_BEFORE", patchValue)));
        document.put("spec", spec);

        return serializer().toYaml(document);
    }

    static String buildLuaScript(List<Map<String, Object>> commands) {
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
        return String.format("""
function envoy_on_request(request_handle)
  local path = request_handle:headers():get(":path")
  if path == nil then
    return
  end
%s  request_handle:headers():replace(":path", path)
end
""", rules);
    }

    void bindManual(PolicyFinder finder, ConversionYamlSupport support) {
        this.policyFinder = finder;
        this.yamlSupport = support;
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
