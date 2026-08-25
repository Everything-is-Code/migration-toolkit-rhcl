package com.redhat.migrationtoolkit.rhcl.util;

/**
 * String helpers for conversion output naming.
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Normalize a 3scale service or backend name to a Kubernetes-friendly kebab-case label.
     */
    public static String toKebabCase(String input) {
        if (input == null) {
            return "service";
        }
        return input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
