package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiKeyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiKeySpec;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ApiProductRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.RequestedBy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.SecretRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(700)
public class ApiKeyGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "apikey.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        return "apiKey".equals(authType);
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;

        ManifestMeta meta = new ManifestMeta(
                name + "-api-key",
                namespace,
                Map.of("app", name, "migrated-from", "3scale"),
                null);

        ApiKeySpec spec = new ApiKeySpec(
                new ApiProductRef(name),
                "basic",
                new RequestedBy("admin@example.com", "admin"),
                new SecretRef(name + "-api-key"));

        ApiKeyManifest manifest = new ApiKeyManifest(
                "devportal.kuadrant.io/v1alpha1", "APIKey", meta, spec);
        return serializer().toYaml(manifest);
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
