package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.DnsPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.DnsPolicySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ProviderRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1800)
public class DnsPolicyGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "dnspolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.emitDnsPolicy();
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;

        ManifestMeta meta = new ManifestMeta(
                name + "-dns-policy",
                namespace,
                Map.of("app", name, "migrated-from", "3scale"),
                null);

        TargetRef targetRef = new TargetRef("gateway.networking.k8s.io", "Gateway", name + "-gateway");

        String providerSecretName = ctx.options.dnsProviderSecretName;
        List<ProviderRef> providerRefs = (providerSecretName != null && !providerSecretName.isBlank())
                ? List.of(new ProviderRef(providerSecretName.trim()))
                : null;

        DnsPolicySpec spec = new DnsPolicySpec(targetRef, providerRefs);
        DnsPolicyManifest manifest = new DnsPolicyManifest("kuadrant.io/v1", "DNSPolicy", meta, spec);
        return serializer().toYaml(manifest);
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
