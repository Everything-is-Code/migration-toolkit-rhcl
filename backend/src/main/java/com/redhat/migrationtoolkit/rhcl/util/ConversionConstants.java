package com.redhat.migrationtoolkit.rhcl.util;

/**
 * Shared semantic defaults for conversion YAML placeholders, scheme ports,
 * and 3scale Admin API list pagination (annotation-safe string companion).
 */
public final class ConversionConstants {

    /** Sentinel written into Secrets / anonymous configs when a real credential is missing. */
    public static final String CREDENTIAL_PLACEHOLDER = "REPLACE_ME";

    /** Generic OIDC issuer used when the 3scale service has no oidcIssuerEndpoint. */
    public static final String DEFAULT_OIDC_ISSUER_URL =
            "https://your-oidc-provider/realms/your-realm";

    /** Default port for in-cluster HTTP backends. */
    public static final int DEFAULT_INTERNAL_PORT = 8080;

    /** Default port for http:// external backends. */
    public static final int DEFAULT_HTTP_PORT = 80;

    /** Default port for https:// external backends (and Gateway HTTPS listener). */
    public static final int DEFAULT_HTTPS_PORT = 443;

    /** Page size for 3scale Admin API list endpoints. */
    public static final int LIST_PAGE_SIZE = 500;

    /**
     * Line separator for generated Kubernetes YAML. Always LF — do not use
     * {@code %n} or {@link System#lineSeparator()} in format strings (Windows emits CRLF).
     */
    public static final String YAML_NEWLINE = "\n";

    /**
     * Compile-time string form of {@link #LIST_PAGE_SIZE} for JAX-RS {@code @DefaultValue}.
     */
    public static final String LIST_PAGE_SIZE_DEFAULT = "500";

    private ConversionConstants() {
    }
}
