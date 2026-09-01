package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeader;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilterBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(250)
public class CorsFiltersContributor implements HttpRouteContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        Policy cors = policyFinder().findEnabled(ctx.service, "cors");
        if (cors == null) {
            return;
        }
        builder.setCorsEnabled(true);
        if (ctx.options.corsNative) {
            builder.setRawCorsFilterYaml(buildNativeCorsFilterYaml(cors));
        } else {
            HTTPRouteFilter filter = buildCorsResponseHeaderFilter(cors);
            if (filter != null) {
                builder.addSharedFilter(filter);
            }
        }
    }

    /**
     * Build YAML fragment for native {@code type: CORS} filter (non-standard, Istio/EnvoyProxy extension).
     * Stored in the builder and injected into rule YAML during serialization.
     * For non-native mode, returns a summary string of all header names for test inspection.
     */
    static String buildCorsFilters(Policy cors, boolean corsNative) {
        if (cors.configuration == null) {
            return "";
        }
        if (corsNative) {
            return buildNativeCorsFilterYaml(cors);
        }
        HTTPRouteFilter filter = buildCorsResponseHeaderFilter(cors);
        if (filter == null) {
            return "";
        }
        // Return a string representation including all set headers (for test-only inspection)
        if (filter.getResponseHeaderModifier() == null) {
            return "ResponseHeaderModifier";
        }
        StringBuilder sb = new StringBuilder("ResponseHeaderModifier");
        if (filter.getResponseHeaderModifier().getSet() != null) {
            for (var h : filter.getResponseHeaderModifier().getSet()) {
                sb.append('\n').append(h.getName()).append(": ").append(h.getValue());
            }
        }
        return sb.toString();
    }

    private static String buildNativeCorsFilterYaml(Policy cors) {
        if (cors.configuration == null) {
            return "";
        }
        Map<String, Object> cfg = cors.configuration;
        List<String> originList = HttpRouteSupport.toStringList(cfg.get("allow_origin")).stream()
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        List<String> methodList = HttpRouteSupport.toStringList(cfg.get("allow_methods")).stream()
                .map(s -> s.trim().toUpperCase()).filter(s -> !s.isBlank()).toList();
        List<String> headerList = HttpRouteSupport.toStringList(cfg.get("allow_headers")).stream()
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        boolean credentials = Boolean.TRUE.equals(cfg.get("allow_credentials"))
                || "true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("allow_credentials", "false")));
        Object maxAgeRaw = cfg.get("max_age");
        Integer maxAge = null;
        if (maxAgeRaw instanceof Number n) {
            maxAge = n.intValue();
        } else if (maxAgeRaw != null) {
            try { maxAge = Integer.parseInt(maxAgeRaw.toString().trim()); }
            catch (NumberFormatException ignored) { maxAge = null; }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- type: CORS\n");
        sb.append("  cors:\n");
        sb.append("    allowOrigins:\n");
        if (originList.isEmpty()) {
            sb.append("    - ").append(HttpRouteSupport.yamlDoubleQuoted("*")).append('\n');
        } else {
            for (String origin : originList) {
                sb.append("    - ").append(HttpRouteSupport.yamlDoubleQuoted(origin)).append('\n');
            }
        }
        if (!methodList.isEmpty()) {
            sb.append("    allowMethods:\n");
            for (String method : methodList) {
                sb.append("    - ").append(method).append('\n');
            }
        }
        if (!headerList.isEmpty()) {
            sb.append("    allowHeaders:\n");
            for (String header : headerList) {
                sb.append("    - ").append(HttpRouteSupport.yamlDoubleQuoted(header)).append('\n');
            }
        }
        if (credentials) {
            sb.append("    allowCredentials: true\n");
        }
        if (maxAge != null) {
            sb.append("    maxAge: ").append(maxAge).append('\n');
        }
        return sb.toString();
    }

    private static HTTPRouteFilter buildCorsResponseHeaderFilter(Policy cors) {
        if (cors.configuration == null) {
            return null;
        }
        Map<String, Object> cfg = cors.configuration;
        List<String> originList = HttpRouteSupport.toStringList(cfg.get("allow_origin")).stream()
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        List<String> methodList = HttpRouteSupport.toStringList(cfg.get("allow_methods")).stream()
                .map(s -> s.trim().toUpperCase()).filter(s -> !s.isBlank()).toList();
        List<String> headerList = HttpRouteSupport.toStringList(cfg.get("allow_headers")).stream()
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        boolean credentials = Boolean.TRUE.equals(cfg.get("allow_credentials"))
                || "true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("allow_credentials", "false")));
        Object maxAgeRaw = cfg.get("max_age");
        Integer maxAge = null;
        if (maxAgeRaw instanceof Number n) {
            maxAge = n.intValue();
        } else if (maxAgeRaw != null) {
            try { maxAge = Integer.parseInt(maxAgeRaw.toString().trim()); }
            catch (NumberFormatException ignored) { maxAge = null; }
        }

        String allowOrigin = "*";
        if (!originList.isEmpty()) {
            allowOrigin = originList.stream().anyMatch("*"::equals) ? "*" : originList.get(0);
        }

        List<HTTPHeader> headers = new java.util.ArrayList<>();
        headers.add(new HTTPHeaderBuilder()
                .withName("Access-Control-Allow-Origin")
                .withValue(allowOrigin)
                .build());
        if (!methodList.isEmpty()) {
            headers.add(new HTTPHeaderBuilder()
                    .withName("Access-Control-Allow-Methods")
                    .withValue(String.join(", ", methodList))
                    .build());
        }
        if (!headerList.isEmpty()) {
            headers.add(new HTTPHeaderBuilder()
                    .withName("Access-Control-Allow-Headers")
                    .withValue(String.join(", ", headerList))
                    .build());
        }
        if (credentials) {
            headers.add(new HTTPHeaderBuilder()
                    .withName("Access-Control-Allow-Credentials")
                    .withValue("true")
                    .build());
        }
        if (maxAge != null) {
            headers.add(new HTTPHeaderBuilder()
                    .withName("Access-Control-Max-Age")
                    .withValue(String.valueOf(maxAge))
                    .build());
        }

        return new HTTPRouteFilterBuilder()
                .withType("ResponseHeaderModifier")
                .withNewResponseHeaderModifier()
                .withSet(headers)
                .endResponseHeaderModifier()
                .build();
    }
}
