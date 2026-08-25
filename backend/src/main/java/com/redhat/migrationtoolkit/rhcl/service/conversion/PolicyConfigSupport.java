package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Shared parsing of 3scale policy configuration maps during YAML generation.
 */
@ApplicationScoped
public class PolicyConfigSupport {

    private static final Logger LOG = Logger.getLogger(PolicyConfigSupport.class);

    public Integer resolveRetryAttempts(Policy retryPolicy) {
        if (retryPolicy == null || retryPolicy.configuration == null) {
            return null;
        }
        Object raw = retryPolicy.configuration.get("retries");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolve request or response byte limit from content_limits configuration.
     */
    public Integer resolveContentLimitBytes(Policy policy, boolean request) {
        if (policy == null || policy.configuration == null) {
            return null;
        }
        Map<String, Object> cfg = policy.configuration;
        Object raw = request
                ? firstNonNull(cfg.get("request"), cfg.get("request_content_limit"))
                : firstNonNull(cfg.get("response"), cfg.get("response_content_limit"));
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    /**
     * Normalize a host or CIDR string. Returns {@code null} for null/blank input.
     */
    public String normalizeCidr(String ip) {
        if (ip == null || ip.isBlank()) {
            LOG.warn("Skipping blank/null CIDR entry in IP check policy");
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.contains("/")) {
            return trimmed;
        }
        if (trimmed.contains(":")) {
            return trimmed + "/128";
        }
        return trimmed + "/32";
    }
}
