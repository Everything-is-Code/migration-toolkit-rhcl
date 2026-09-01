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
@Priority(1100)
public class LoggingEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ConversionYamlSupport yamlSupport;

    @Inject
    ManifestSerializer manifestSerializer;

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
        String context = isGateway ? "GATEWAY" : "SIDECAR_INBOUND";

        Map<String, Object> jsonFormat = new LinkedHashMap<>();
        for (Map<String, Object> entry : jsonCfg) {
            String key = String.valueOf(entry.getOrDefault("key", ""));
            String value = String.valueOf(entry.getOrDefault("value", ""));
            jsonFormat.put(key, ConversionYamlSupport.toEnvoyVar(value));
        }

        Map<String, Object> patchValue = Map.of(
                "typed_config", Map.of(
                        "@type", "type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager",
                        "access_log", List.of(Map.of(
                                "name", "envoy.access_loggers.stdout",
                                "typed_config", Map.of(
                                        "@type", "type.googleapis.com/envoy.extensions.access_loggers.stream.v3.StdoutAccessLog",
                                        "log_format", Map.of("json_format", jsonFormat))))));

        Map<String, Object> document = EnvoyFilterManifests.baseDocument(
                name, namespace, name + "-logging-format", ctx.includeMigratedFromLabel);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        metadata.put("annotations", Map.of("3scale-migration/source", "logging"));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workloadSelector", Map.of(
                "labels", IstioManifestSupport.loggingWorkloadLabels(name, isGateway)));
        EnvoyFilterManifests.withConfigPatches(
                spec, List.of(EnvoyFilterManifests.networkFilterPatch(context, patchValue)));
        document.put("spec", spec);

        return serializer().toYaml(document);
    }

    void bindManual(PolicyFinder finder, ConversionYamlSupport support) {
        this.policyFinder = finder;
        this.yamlSupport = support;
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
