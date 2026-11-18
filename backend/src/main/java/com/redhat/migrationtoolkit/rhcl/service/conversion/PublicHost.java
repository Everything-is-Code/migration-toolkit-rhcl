package com.redhat.migrationtoolkit.rhcl.service.conversion;

import java.net.URI;

/**
 * Public app hostname and cluster Service identity derived from 3scale URLs.
 */
public final class PublicHost {

    private PublicHost() {
    }

    /**
     * {@code name-namespace.apps...} when the OIDC issuer host is {@code *.apps.*}.
     */
    public static String fromIssuer(String issuer, String name, String namespace) {
        if (issuer == null || name == null || name.isBlank() || namespace == null || namespace.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(issuer).getHost();
            if (host == null) {
                return null;
            }
            int dot = host.indexOf('.');
            if (dot < 1 || !host.substring(dot + 1).startsWith("apps.")) {
                return null;
            }
            return name + "-" + namespace + "." + host.substring(dot + 1);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Namespace embedded in {@code svc.namespace.svc.cluster.local}, or null. */
    public static String namespaceOf(String endpoint) {
        String[] parts = hostParts(endpoint);
        if (parts.length >= 3 && "svc".equals(parts[2])) {
            return parts[1];
        }
        return null;
    }

    /**
     * Remove the backend usage path that 3scale prepends to a public pattern.
     * A usage path of {@code /} is already the public path.
     */
    public static String stripUsagePrefix(String pattern, String usagePath) {
        if (pattern == null || usagePath == null || usagePath.isBlank() || "/".equals(usagePath)) {
            return pattern;
        }
        String prefix = usagePath.endsWith("/") ? usagePath.substring(0, usagePath.length() - 1) : usagePath;
        if (pattern.equals(prefix)) {
            return "/";
        }
        if (pattern.startsWith(prefix + "/")) {
            return pattern.substring(prefix.length());
        }
        return pattern;
    }

    private static String[] hostParts(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return new String[0];
        }
        try {
            String value = endpoint.trim();
            if (!value.contains("://")) {
                value = "http://" + value;
            }
            String host = URI.create(value).getHost();
            if (host == null || host.isBlank()) {
                return new String[0];
            }
            return host.split("\\.");
        } catch (RuntimeException e) {
            return new String[0];
        }
    }
}
