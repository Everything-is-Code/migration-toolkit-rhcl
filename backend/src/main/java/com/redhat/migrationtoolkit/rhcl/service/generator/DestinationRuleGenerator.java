package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.istio.api.api.networking.v1alpha3.ClientTLSSettingsTLSmode;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Priority(900)
public class DestinationRuleGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    @Override
    public String outputKey() {
        return "destinationrule.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.resolvedBackends.stream().anyMatch(b -> b.type == BackendType.EXTERNAL);
    }

    @Override
    public String generate(ConversionContext ctx) {
        List<ResolvedBackend> externals = ctx.resolvedBackends.stream()
                .filter(b -> b.type == BackendType.EXTERNAL)
                .toList();
        return IstioManifestSupport.joinYamlChunks(externals.stream()
                .map(b -> generateOne(b, ctx))
                .toArray(String[]::new));
    }

    private String generateOne(ResolvedBackend backend, ConversionContext ctx) {
        var spec = new DestinationRuleBuilder()
                .withApiVersion("networking.istio.io/v1alpha3")
                .withKind("DestinationRule")
                .withNewMetadata()
                .withName(backend.drName)
                .withNamespace(ctx.namespace)
                .withLabels(IstioManifestSupport.baseLabels(ctx.serviceKebabName, ctx.includeMigratedFromLabel))
                .endMetadata()
                .withNewSpec()
                .withHost(backend.externalHost)
                .withNewTrafficPolicy();

        if (backend.usesTls) {
            spec.withNewTls()
                    .withMode(ClientTLSSettingsTLSmode.SIMPLE)
                    .withSni(backend.externalHost)
                    .endTls();
        } else {
            spec.withNewTls()
                    .withMode(ClientTLSSettingsTLSmode.DISABLE)
                    .endTls();
        }

        DestinationRule destinationRule = spec
                .endTrafficPolicy()
                .endSpec()
                .build();

        return serializer().toYaml(destinationRule);
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
