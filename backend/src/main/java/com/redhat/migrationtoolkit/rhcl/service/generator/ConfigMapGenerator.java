package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(500)
public class ConfigMapGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "configmap.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        var backends = ctx.resolvedBackends;
        String primary = "";
        if (backends != null && !backends.isEmpty()
                && backends.get(0).privateEndpoint != null
                && !backends.get(0).privateEndpoint.isBlank()) {
            primary = backends.get(0).privateEndpoint.trim();
        }
        String allUrls = backends == null ? "" : backends.stream()
                .map(b -> b.privateEndpoint)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        String overrideNote = ctx.overrideIgnored ? "ignored-multi-backend" : "not-applicable";

        var configMapBuilder = new ConfigMapBuilder()
                .withApiVersion("v1")
                .withKind("ConfigMap")
                .withNewMetadata()
                .withName(name + "-config")
                .withNamespace(namespace)
                .addToLabels("app", name);
        if (ctx.includeMigratedFromLabel) {
            configMapBuilder.addToLabels("migrated-from", "3scale");
        }
        var configMap = configMapBuilder
                .endMetadata()
                .addToData("backend-url", primary)
                .addToData("backend-urls", allUrls)
                .addToData("external-backend-url-override", overrideNote)
                .addToData("service-name", ctx.service.name != null ? ctx.service.name : "")
                .addToData("original-3scale-service-id", ctx.service.id != null ? ctx.service.id : "")
                .build();

        return serializer().toYaml(configMap);
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
