package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

/**
 * Mutable accumulator for Secret YAML assembled by contributors (first match wins).
 */
public final class SecretBuilder {

    private final String name;
    private final String namespace;
    private String secretYaml;
    private String discoveryMarker;

    public SecretBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public boolean hasSecret() {
        return secretYaml != null && !secretYaml.isBlank();
    }

    public void setSecretYaml(String yaml) {
        this.secretYaml = yaml;
    }

    public void setDiscoveryMarker(String marker) {
        this.discoveryMarker = marker;
    }

    public String build() {
        if (!hasSecret()) {
            return "";
        }
        if (discoveryMarker == null) {
            return secretYaml;
        }
        if (secretYaml.contains("  annotations:\n")) {
            return secretYaml.replace("  annotations:\n", "  annotations:\n    " + discoveryMarker + "\n");
        }
        int typeIdx = secretYaml.indexOf("type: Opaque");
        if (typeIdx < 0) {
            return secretYaml;
        }
        String before = secretYaml.substring(0, typeIdx);
        String after = secretYaml.substring(typeIdx);
        if (!before.endsWith("\n")) {
            before += "\n";
        }
        return before + "  annotations:\n    " + discoveryMarker + "\n" + after;
    }
}
