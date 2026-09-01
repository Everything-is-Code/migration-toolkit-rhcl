package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductSpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(600)
public class ApiProductGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "apiproduct.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String displayName = ctx.service.name != null ? ctx.service.name : name;
        // Jackson handles quoting — no manual replace("\"", "'") needed
        String description = ctx.service.description != null ? ctx.service.description : "Migrated from 3scale";

        ManifestMeta meta = new ManifestMeta(
                name,
                namespace,
                Map.of("app", name, "migrated-from", "3scale"),
                null);

        TargetRef targetRef = new TargetRef("gateway.networking.k8s.io", "HTTPRoute", name + "-route");
        ApiProductSpec spec = new ApiProductSpec(
                displayName,
                description,
                "automatic",
                "Published",
                targetRef,
                "v1");

        ApiProductManifest manifest = new ApiProductManifest(
                "devportal.kuadrant.io/v1alpha1", "APIProduct", meta, spec);
        return serializer().toYaml(manifest);
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
