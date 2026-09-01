package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.istio.api.api.networking.v1alpha3.ServiceEntryLocation;
import io.fabric8.istio.api.api.networking.v1alpha3.ServiceEntryResolution;
import io.fabric8.istio.api.networking.v1alpha3.ServiceEntry;
import io.fabric8.istio.api.networking.v1alpha3.ServiceEntryBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Priority(800)
public class ServiceEntryGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "serviceentry.yaml";
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
        String portName = backend.usesTls ? "https" : "http";
        String protocol = backend.usesTls ? "HTTPS" : "HTTP";

        ServiceEntry serviceEntry = new ServiceEntryBuilder()
                .withApiVersion("networking.istio.io/v1alpha3")
                .withKind("ServiceEntry")
                .withNewMetadata()
                .withName(backend.seName)
                .withNamespace(ctx.namespace)
                .withLabels(IstioManifestSupport.baseLabels(ctx.serviceKebabName, ctx.includeMigratedFromLabel))
                .endMetadata()
                .withNewSpec()
                .withHosts(backend.externalHost)
                .addNewPort()
                .withNumber((long) backend.port)
                .withName(portName)
                .withProtocol(protocol)
                .endPort()
                .withResolution(ServiceEntryResolution.DNS)
                .withLocation(ServiceEntryLocation.MESH_EXTERNAL)
                .endSpec()
                .build();

        Service externalNameService = new ServiceBuilder()
                .withApiVersion("v1")
                .withKind("Service")
                .withNewMetadata()
                .withName(backend.refName)
                .withNamespace(ctx.namespace)
                .withLabels(IstioManifestSupport.baseLabels(ctx.serviceKebabName, ctx.includeMigratedFromLabel))
                .endMetadata()
                .withNewSpec()
                .withType("ExternalName")
                .withExternalName(backend.externalHost)
                .withPorts(new ServicePortBuilder()
                        .withName(portName)
                        .withPort(backend.port)
                        .withTargetPort(new IntOrString(portName))
                        .build())
                .endSpec()
                .build();

        return IstioManifestSupport.joinDocuments(serializer(), serviceEntry, externalNameService);
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
