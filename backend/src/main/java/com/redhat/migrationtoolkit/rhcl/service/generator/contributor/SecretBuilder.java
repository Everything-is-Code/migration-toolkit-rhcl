package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import io.fabric8.kubernetes.api.model.Secret;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;

/**
 * Mutable accumulator for Secret YAML assembled by contributors (first match wins).
 * Wraps a Fabric8 {@link io.fabric8.kubernetes.api.model.SecretBuilder} internally.
 */
public final class SecretBuilder {

    private static final Logger LOG = Logger.getLogger(SecretBuilder.class);

    private final String name;
    private final String namespace;
    private final boolean includeMigratedFromLabel;
    private io.fabric8.kubernetes.api.model.SecretBuilder fabric8Builder;
    private String yamlCommentPrefix = "";
    private String discoveryMarker;

    public SecretBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
        this.includeMigratedFromLabel = ctx.includeMigratedFromLabel;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public boolean hasSecret() {
        return fabric8Builder != null;
    }

    /**
     * Starts an Opaque Secret with standard migration labels. No-op if a secret was already started.
     */
    public void beginOpaqueSecret(String resourceName) {
        if (hasSecret()) {
            return;
        }
        var metadata = new io.fabric8.kubernetes.api.model.SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .addToLabels("app", name);
        if (includeMigratedFromLabel) {
            metadata.addToLabels("migrated-from", "3scale");
        }
        fabric8Builder = metadata.endMetadata().withType("Opaque");
    }

    public void addLabel(String key, String value) {
        ensureStarted();
        fabric8Builder.editMetadata().addToLabels(key, value).endMetadata();
    }

    public void addStringData(String key, String value) {
        ensureStarted();
        fabric8Builder.addToStringData(key, value != null ? value : "");
    }

    /**
     * YAML comment lines inserted immediately before {@code stringData:} in serialized output.
     */
    public void setYamlCommentPrefix(String prefix) {
        this.yamlCommentPrefix = prefix != null ? prefix : "";
    }

    public void setDiscoveryMarker(String marker) {
        this.discoveryMarker = marker;
    }

    public String build() {
        return build(resolveSerializer());
    }

    public String build(ManifestSerializer serializer) {
        if (!hasSecret()) {
            return "";
        }
        if (discoveryMarker != null && !discoveryMarker.isBlank()) {
            applyDiscoveryMarker(discoveryMarker.trim());
        }
        Secret secret = fabric8Builder.build();
        if (secret.getStringData() == null || secret.getStringData().isEmpty()) {
            secret = new io.fabric8.kubernetes.api.model.SecretBuilder(secret)
                    .withStringData(new LinkedHashMap<>())
                    .build();
        }
        String yaml = serializer.toYaml(secret);
        if (!yaml.contains("stringData:")) {
            yaml = injectEmptyStringData(yaml);
        }
        if (!yamlCommentPrefix.isBlank()) {
            yaml = injectYamlCommentBeforeStringData(yaml, yamlCommentPrefix);
        }
        return yaml;
    }

    private void ensureStarted() {
        if (!hasSecret()) {
            throw new IllegalStateException("Secret not started — call beginOpaqueSecret() first");
        }
    }

    private void applyDiscoveryMarker(String marker) {
        int colon = marker.indexOf(':');
        if (colon <= 0) {
            LOG.warnf("Ignoring malformed discovery marker (expected key: value): %s", marker);
            return;
        }
        String key = marker.substring(0, colon).trim();
        String value = marker.substring(colon + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            LOG.warnf("Ignoring malformed discovery marker (empty key or value): %s", marker);
            return;
        }
        fabric8Builder.editMetadata().addToAnnotations(key, value).endMetadata();
    }

    /**
     * Fabric8 does not emit YAML comments; inject WARNING lines before {@code stringData:},
     * or before {@code type:} when {@code stringData} is absent from serialized output.
     */
    static String injectYamlCommentBeforeStringData(String yaml, String prefix) {
        int stringDataIdx = yaml.indexOf("stringData:");
        if (stringDataIdx >= 0) {
            return yaml.substring(0, stringDataIdx) + prefix + yaml.substring(stringDataIdx);
        }
        int typeIdx = indexOfOpaqueType(yaml);
        if (typeIdx >= 0) {
            return yaml.substring(0, typeIdx) + prefix + yaml.substring(typeIdx);
        }
        return yaml + prefix;
    }

    static String injectEmptyStringData(String yaml) {
        int typeIdx = indexOfOpaqueType(yaml);
        if (typeIdx < 0) {
            return yaml + "stringData: {}\n";
        }
        int lineEnd = yaml.indexOf('\n', typeIdx);
        if (lineEnd < 0) {
            return yaml + "\nstringData: {}\n";
        }
        return yaml.substring(0, lineEnd + 1) + "stringData: {}\n" + yaml.substring(lineEnd + 1);
    }

    private static int indexOfOpaqueType(String yaml) {
        for (String needle : new String[] {"type: Opaque", "type: \"Opaque\"", "type: 'Opaque'"}) {
            int idx = yaml.indexOf(needle);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    /** CDI-free fallback for static contributor helpers and isolated unit tests. */
    private static ManifestSerializer resolveSerializer() {
        return new ManifestSerializer();
    }
}
