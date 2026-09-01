package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.IssuerRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TlsPolicySpec;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.KuadrantManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(1700)
public class TlsPolicyGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "tlspolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return ctx.options.includeTlsPolicy;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String issuerKind = ctx.options.tlsIssuerKind;
        String issuerName = ctx.options.tlsIssuerName;
        String kind = (issuerKind != null && !issuerKind.isBlank()) ? issuerKind : "ClusterIssuer";
        String issuer = (issuerName != null && !issuerName.isBlank()) ? issuerName : "letsencrypt-prod";

        ManifestMeta meta = KuadrantManifestSupport.meta(
                name + "-tls-policy", namespace, name, ctx.includeMigratedFromLabel);

        TargetRef targetRef = new TargetRef("gateway.networking.k8s.io", "Gateway", name + "-gateway");
        IssuerRef issuerRef = new IssuerRef("cert-manager.io", kind, issuer);
        TlsPolicySpec spec = new TlsPolicySpec(targetRef, issuerRef);

        TlsPolicyManifest manifest = new TlsPolicyManifest("kuadrant.io/v1", "TLSPolicy", meta, spec);
        return serializer().toYaml(manifest);
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
