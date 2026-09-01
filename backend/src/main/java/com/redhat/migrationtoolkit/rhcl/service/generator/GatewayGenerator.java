package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.Gateway;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.GatewayBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.Listener;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ListenerBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Priority(100)
public class GatewayGenerator implements ResourceGenerator {

    @Inject
    ManifestSerializer manifestSerializer;

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "gateway.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return !RegistryDiscoveryMarkers.isDiscoveryService(ctx);
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        String hostname = resolveHostname(ctx);

        List<Listener> listeners = new ArrayList<>();
        listeners.add(httpListener(hostname));
        listeners.add(httpsListener(name, hostname));

        var metadata = new GatewayBuilder()
                .withApiVersion("gateway.networking.k8s.io/v1")
                .withKind("Gateway")
                .withNewMetadata()
                .withName(name + "-gateway")
                .withNamespace(namespace)
                .addToLabels("app", name);
        if (ctx.includeMigratedFromLabel) {
            metadata.addToLabels("migrated-from", "3scale");
        }
        Gateway gateway = metadata
                .endMetadata()
                .withNewSpec()
                .withGatewayClassName("istio")
                .withListeners(listeners)
                .endSpec()
                .build();

        return serializer().toYaml(gateway);
    }

    private static Listener httpListener(String hostname) {
        ListenerBuilder builder = new ListenerBuilder()
                .withName("http")
                .withProtocol("HTTP")
                .withPort(ConversionConstants.DEFAULT_HTTP_PORT)
                .withNewAllowedRoutes()
                .withNewNamespaces()
                .withFrom("Same")
                .endNamespaces()
                .endAllowedRoutes();
        if (hostname != null) {
            builder.withHostname(hostname);
        }
        return builder.build();
    }

    private static Listener httpsListener(String name, String hostname) {
        ListenerBuilder builder = new ListenerBuilder()
                .withName("https")
                .withProtocol("HTTPS")
                .withPort(ConversionConstants.DEFAULT_HTTPS_PORT)
                .withNewTls()
                .withMode("Terminate")
                .addNewCertificateRef()
                .withName(name + "-tls")
                .endCertificateRef()
                .endTls()
                .withNewAllowedRoutes()
                .withNewNamespaces()
                .withFrom("Same")
                .endNamespaces()
                .endAllowedRoutes();
        if (hostname != null) {
            builder.withHostname(hostname);
        }
        return builder.build();
    }

    private static String resolveHostname(ConversionContext ctx) {
        if (!ctx.emitDnsPolicy()) {
            return null;
        }
        String hostname = ctx.options.dnsHostname;
        if (hostname == null || hostname.isBlank()) {
            return null;
        }
        return hostname.trim();
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
