package com.redhat.migrationtoolkit.rhcl.service.conversion;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML string helpers for conversion output (labels, JSON config parsing, line endings).
 */
@ApplicationScoped
public class ConversionYamlSupport {

    private static final Logger LOG = Logger.getLogger(ConversionYamlSupport.class);

    /** Remove the {@code migrated-from: 3scale} label line when the user opts out. */
    public String stripMigratedFromLabel(String content) {
        return content.replaceAll("(?m)^[ \\t]*migrated-from: 3scale\\R?", "");
    }

    /**
     * Normalize generated YAML to LF. {@link String#format} {@code %n} uses the platform
     * line separator (CRLF on Windows); Kubernetes manifests and tests expect LF.
     */
    public static String normalizeLineEndings(String content) {
        if (content == null || content.indexOf('\r') < 0) {
            return content;
        }
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseJsonObjectConfig(Object raw) {
        if (raw instanceof List) {
            return (List<Map<String, Object>>) raw;
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JavaType type = om.getTypeFactory()
                        .constructCollectionType(List.class,
                                om.getTypeFactory().constructMapType(
                                        LinkedHashMap.class, String.class, Object.class));
                return om.readValue(str, type);
            } catch (Exception e) {
                LOG.warnf("Failed to parse json_object_config string: %s", e.getMessage());
            }
        }
        return List.of();
    }

    /** Map 3scale nginx variables to Envoy access log variables. */
    public static String toEnvoyVar(String nginxValue) {
        return nginxValue
                .replace("$request_method",  "%REQ(:METHOD)%")
                .replace("$request_uri",     "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%%QUERY_STRING%")
                .replace("$uri",             "%REQ(X-ENVOY-ORIGINAL-PATH?:PATH)%")
                .replace("$status",          "%RESPONSE_CODE%")
                .replace("$remote_addr",     "%DOWNSTREAM_REMOTE_ADDRESS_WITHOUT_PORT%")
                .replace("$bytes_sent",      "%BYTES_SENT%")
                .replace("$request_time",    "%DURATION%")
                .replace("$http_user_agent", "%REQ(USER-AGENT)%")
                .replace("$http_referer",    "%REQ(REFERER)%");
    }

    /** Best-effort conversion of common PCRE notations to Lua patterns. */
    public static String pcreToLuaPattern(String pcre) {
        return pcre
                .replace("\\d", "%d")
                .replace("\\w", "%w")
                .replace("\\s", "%s")
                .replace("\\.", "%.");
    }

    /** Convert 3scale replacement strings ($1 / \\1) to Lua's %1 format. */
    public static String pcreReplaceToLua(String replace) {
        return replace
                .replaceAll("\\$(\\d)", "%$1")
                .replaceAll("\\\\(\\d)", "%$1");
    }
}
