package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

/**
 * Merges named authorization rule blocks into AuthPolicy YAML.
 */
public final class AuthPolicyYamlMerger {

    private AuthPolicyYamlMerger() {
    }

    /**
     * Merge a named authorization rule body (indented under {@code authorization:}) into AuthPolicy YAML.
     * Creates the {@code authorization:} map when missing; otherwise inserts as a sibling entry.
     */
    public static String mergeAuthorizationNamedRules(String authPolicyYaml, String namedRuleBlock) {
        if (authPolicyYaml == null || authPolicyYaml.isBlank()
                || namedRuleBlock == null || namedRuleBlock.isBlank()) {
            return authPolicyYaml;
        }
        String block = namedRuleBlock;
        if (block.startsWith("    authorization:\n")) {
            block = block.substring("    authorization:\n".length());
        }
        if (!block.endsWith("\n")) {
            block = block + "\n";
        }
        String marker = "\n    authorization:";
        int authIdx = authPolicyYaml.indexOf(marker);
        if (authIdx < 0) {
            return authPolicyYaml.stripTrailing() + "\n    authorization:\n" + block;
        }
        int insertAt = authIdx + marker.length();
        int lineEnd = authPolicyYaml.indexOf('\n', insertAt);
        if (lineEnd < 0) {
            return authPolicyYaml + "\n" + block;
        }
        return authPolicyYaml.substring(0, lineEnd + 1) + block + authPolicyYaml.substring(lineEnd + 1);
    }
}
