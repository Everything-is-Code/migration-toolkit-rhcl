package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.EnvoyFilterManifests;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1250)
public class MaintenanceModeEnvoyFilterGenerator implements ResourceGenerator {

    private static final String DEFAULT_STATUS = "503";
    private static final String DEFAULT_CONTENT_TYPE = "text/plain";

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ManifestSerializer manifestSerializer;

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
        String luaScript = String.format("""
function envoy_on_request(request_handle)
  request_handle:respond(
    {[":status"] = "%s", ["content-type"] = "%s"},
    "%s")
end
""", escapeLua(status), escapeLua(contentType), escapeLua(message));

        Map<String, Object> patchValue = Map.of(
                "name", "envoy.filters.http.lua",
                "typed_config", Map.of(
                        "@type", "type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua",
                        "inlineCode", luaScript));

        Map<String, Object> document = EnvoyFilterManifests.baseDocument(
                name, namespace, name + "-maintenance", ctx.includeMigratedFromLabel);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        metadata.put("annotations", Map.of("3scale-migration/source", "maintenance_mode"));

        Map<String, Object> spec = EnvoyFilterManifests.gatewayWorkloadSpec(name);
        EnvoyFilterManifests.withConfigPatches(
                spec, List.of(EnvoyFilterManifests.httpFilterGatewayPatch("INSERT_BEFORE", patchValue)));
        document.put("spec", spec);

        return serializer().toYaml(document);
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

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
