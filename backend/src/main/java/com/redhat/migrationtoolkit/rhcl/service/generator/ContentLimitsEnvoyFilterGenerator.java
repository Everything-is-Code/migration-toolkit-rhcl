package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.EnvoyFilterManifests;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1300)
public class ContentLimitsEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Inject
    ManifestSerializer manifestSerializer;

    @Override
    public String outputKey() {
        return "envoyfilter-content-limits.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy contentLimits = policyFinder.findEnabledAny(
                ctx.service, true, "content_limits", "payload_limits");
        if (contentLimits == null) {
            return false;
        }
        Integer requestBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, true);
        return requestBytes != null && requestBytes > 0;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy contentLimits = policyFinder.findEnabledAny(
                ctx.service, true, "content_limits", "payload_limits");
        Integer requestBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, true);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;

        Map<String, Object> patchValue = Map.of(
                "name", "envoy.filters.http.buffer",
                "typed_config", Map.of(
                        "@type", "type.googleapis.com/envoy.extensions.filters.http.buffer.v3.Buffer",
                        "max_request_bytes", requestBytes));

        Map<String, Object> document = EnvoyFilterManifests.baseDocument(
                name, namespace, name + "-content-limits", ctx.includeMigratedFromLabel);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
        metadata.put("annotations", Map.of("3scale-migration/source", "content_limits"));

        Map<String, Object> spec = EnvoyFilterManifests.gatewayWorkloadSpec(name);
        EnvoyFilterManifests.withConfigPatches(
                spec, List.of(EnvoyFilterManifests.httpFilterGatewayPatch("INSERT_BEFORE", patchValue)));
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
