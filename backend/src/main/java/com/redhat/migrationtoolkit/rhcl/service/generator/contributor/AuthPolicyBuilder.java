package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator for AuthPolicy YAML assembled by contributors.
 */
public final class AuthPolicyBuilder {

    private final String name;
    private final String namespace;
    private String baseYaml;
    private String authCacheBlock = "";
    private final List<String> authorizationRuleBlocks = new ArrayList<>();
    private String discoveryMarker = null;

    public AuthPolicyBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public boolean hasBase() {
        return baseYaml != null && !baseYaml.isBlank();
    }

    public void setBaseYaml(String yaml) {
        this.baseYaml = yaml;
    }

    public void setAuthCacheBlock(String block) {
        this.authCacheBlock = block != null ? block : "";
    }

    public String authCacheBlock() {
        return authCacheBlock;
    }

    public void appendAuthorizationRule(String namedRuleBlock) {
        if (namedRuleBlock != null && !namedRuleBlock.isBlank()) {
            authorizationRuleBlocks.add(namedRuleBlock);
        }
    }

    public void setDiscoveryMarker(String marker) {
        this.discoveryMarker = marker;
    }

    public String build() {
        if (!hasBase()) {
            return "";
        }
        String yaml = baseYaml;
        if (!authCacheBlock.isEmpty() && yaml.contains("%s")) {
            yaml = yaml.formatted(authCacheBlock);
        }
        for (String block : authorizationRuleBlocks) {
            yaml = AuthPolicyYamlMerger.mergeAuthorizationNamedRules(yaml, block);
        }
        if (discoveryMarker != null) {
            yaml = injectDiscoveryMarker(yaml, discoveryMarker);
        }
        return yaml;
    }

    private static String injectDiscoveryMarker(String yaml, String marker) {
        if (yaml.contains("  annotations:\n")) {
            return yaml.replace("  annotations:\n", "  annotations:\n    " + marker + "\n");
        }
        int specIdx = yaml.indexOf("spec:");
        if (specIdx < 0) {
            return yaml;
        }
        String header = yaml.substring(0, specIdx);
        String tail = yaml.substring(specIdx);
        if (!header.endsWith("\n")) {
            header += "\n";
        }
        return header + "  annotations:\n    " + marker + "\n" + tail;
    }
}
