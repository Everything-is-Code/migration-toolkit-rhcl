package com.redhat.migrationtoolkit.rhcl.util;

import org.jboss.logging.Logger;

import java.net.URI;

/**
 * URL parsing helpers for backend resolution during conversion.
 */
public final class UrlUtils {

    private static final Logger LOG = Logger.getLogger(UrlUtils.class);

    private UrlUtils() {
    }

    /** Extract the hostname from a URL. Returns null on failure. */
    public static String extractHostname(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "https://" + s;
            }
            return new URI(s).getHost();
        } catch (Exception e) {
            LOG.debugf("Failed to extract hostname from URL '%s': %s", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extract the service name from an internal backend URL.
     * Falls back to {@code name + "-backend"} if extraction fails.
     */
    public static String extractInternalService(String url, String name) {
        String host = extractHostname(url);
        if (host == null || host.isBlank()) {
            return name + "-backend";
        }
        return host.split("\\.")[0];
    }

    /** Extract the port number from a URL. Returns the default value on failure. */
    public static int extractPort(String url, int defaultPort) {
        if (url == null || url.isBlank()) {
            return defaultPort;
        }
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                s = "http://" + s;
            }
            int port = new URI(s).getPort();
            return port > 0 ? port : defaultPort;
        } catch (Exception e) {
            LOG.debugf("Failed to extract port from URL '%s': %s", url, e.getMessage());
            return defaultPort;
        }
    }
}
