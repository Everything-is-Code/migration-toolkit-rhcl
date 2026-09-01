package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.EnvoyFilterManifests;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1400)
public class RetryEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Inject
    ManifestSerializer manifestSerializer;

    @Override
    public String outputKey() {
        return "envoyfilter-retry.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        if (ctx.options.retriesSupported) {
            return false;
        }
        Integer retries = policyConfigSupport.resolveRetryAttempts(
                policyFinder.findEnabled(ctx.service, "retry"));
        return retries != null && retries > 0;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Integer retries = policyConfigSupport.resolveRetryAttempts(
                policyFinder.findEnabled(ctx.service, "retry"));
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;

        Map<String, Object> patchValue = Map.of(
                "route", Map.of(
                        "retry_policy", Map.of(
                                "retry_on", "5xx,reset,connect-failure,refused-stream",
                                "num_retries", retries)));

        Map<String, Object> document = EnvoyFilterManifests.baseDocument(
                name, namespace, name + "-retry", ctx.includeMigratedFromLabel);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("3scale-migration/source", "retry");
        annotations.put(
                "3scale-migration/note",
                "HTTPRoute retry unsupported on this cluster — Envoy route retry_policy fallback");
        metadata.put("annotations", annotations);

        Map<String, Object> spec = EnvoyFilterManifests.gatewayWorkloadSpec(name);
        EnvoyFilterManifests.withConfigPatches(spec, List.of(EnvoyFilterManifests.httpRouteGatewayPatch(patchValue)));
        document.put("spec", spec);

        return serializer().toYaml(document);
    }

    void bindManual(PolicyFinder finder, PolicyConfigSupport support) {
        this.policyFinder = finder;
        this.policyConfigSupport = support;
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
