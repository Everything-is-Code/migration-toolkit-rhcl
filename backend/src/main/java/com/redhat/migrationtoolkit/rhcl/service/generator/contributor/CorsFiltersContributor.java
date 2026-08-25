package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
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
        builder.appendSharedFilters(buildCorsFilters(cors, ctx.options.corsNative));
    }

    static String buildCorsFilters(Policy cors, boolean corsNative) {
        if (cors.configuration == null) {
            return "";
        }
        Map<String, Object> cfg = cors.configuration;

        List<String> originList = HttpRouteSupport.toStringList(cfg.get("allow_origin")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        List<String> methodList = HttpRouteSupport.toStringList(cfg.get("allow_methods")).stream()
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .toList();
        List<String> headerList = HttpRouteSupport.toStringList(cfg.get("allow_headers")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        boolean credentials = Boolean.TRUE.equals(cfg.get("allow_credentials"))
                || "true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("allow_credentials", "false")));
        Object maxAgeRaw = cfg.get("max_age");
        Integer maxAge = null;
        if (maxAgeRaw instanceof Number n) {
            maxAge = n.intValue();
        } else if (maxAgeRaw != null) {
            try {
                maxAge = Integer.parseInt(maxAgeRaw.toString().trim());
            } catch (NumberFormatException ignored) {
                maxAge = null;
            }
        }

        if (corsNative) {
            return buildNativeCorsFilter(originList, methodList, headerList, credentials, maxAge);
        }
        return buildCorsResponseHeaderModifier(originList, methodList, headerList, credentials, maxAge);
    }

    private static String buildNativeCorsFilter(List<String> originList,
                                                List<String> methodList,
                                                List<String> headerList,
                                                boolean credentials,
                                                Integer maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append("        - type: CORS\n");
        sb.append("          cors:\n");
        sb.append("            allowOrigins:\n");
        if (originList.isEmpty()) {
            sb.append("              - ").append(HttpRouteSupport.yamlDoubleQuoted("*")).append('\n');
        } else {
            for (String origin : originList) {
                sb.append("              - ").append(HttpRouteSupport.yamlDoubleQuoted(origin)).append('\n');
            }
        }
        if (!methodList.isEmpty()) {
            sb.append("            allowMethods:\n");
            for (String method : methodList) {
                sb.append("              - ").append(method).append('\n');
            }
        }
        if (!headerList.isEmpty()) {
            sb.append("            allowHeaders:\n");
            for (String header : headerList) {
                sb.append("              - ").append(HttpRouteSupport.yamlDoubleQuoted(header)).append('\n');
            }
        }
        if (credentials) {
            sb.append("            allowCredentials: true\n");
        }
        if (maxAge != null) {
            sb.append("            maxAge: ").append(maxAge).append('\n');
        }
        return sb.toString();
    }

    private static String buildCorsResponseHeaderModifier(List<String> originList,
                                                          List<String> methodList,
                                                          List<String> headerList,
                                                          boolean credentials,
                                                          Integer maxAge) {
        String allowOrigin = "*";
        if (!originList.isEmpty()) {
            allowOrigin = originList.stream().anyMatch("*"::equals) ? "*" : originList.get(0);
        }

        StringBuilder setHeaders = new StringBuilder();
        setHeaders.append(String.format(
                "              - name: Access-Control-Allow-Origin%n                value: %s%n",
                HttpRouteSupport.yamlDoubleQuoted(allowOrigin)));
        if (!methodList.isEmpty()) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Methods%n                value: %s%n",
                    HttpRouteSupport.yamlDoubleQuoted(String.join(", ", methodList))));
        }
        if (!headerList.isEmpty()) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Headers%n                value: %s%n",
                    HttpRouteSupport.yamlDoubleQuoted(String.join(", ", headerList))));
        }
        if (credentials) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Allow-Credentials%n                value: \"true\"%n"));
        }
        if (maxAge != null) {
            setHeaders.append(String.format(
                    "              - name: Access-Control-Max-Age%n                value: \"%d\"%n",
                    maxAge));
        }

        return "        - type: ResponseHeaderModifier\n"
                + "          responseHeaderModifier:\n"
                + "            set:\n"
                + setHeaders;
    }
}
