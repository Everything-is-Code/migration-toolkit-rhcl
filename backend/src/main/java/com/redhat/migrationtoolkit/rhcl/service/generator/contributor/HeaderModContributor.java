package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(200)
public class HeaderModContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        builder.appendSharedFilters(buildHeaderModificationFilters(ctx.service));
    }

    @SuppressWarnings("unchecked")
    static String buildHeaderModificationFilters(ApiService service) {
        Policy policy = HttpRouteSupport.findHeaderModificationPolicy(service);
        if (policy == null || policy.configuration == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (String direction : new String[]{"response", "request"}) {
            Object raw = policy.configuration.get(direction);
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                continue;
            }

            StringBuilder setHeaders = new StringBuilder();
            StringBuilder addHeaders = new StringBuilder();
            StringBuilder removeHeaders = new StringBuilder();

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object hRaw = entry.get("header");
                Object vRaw = entry.get("value");
                Object oRaw = entry.get("op");
                Object tRaw = entry.get("value_type");
                String headerRaw = (hRaw != null ? hRaw.toString() : "").replace(":", "").trim();
                String value = vRaw != null ? vRaw.toString() : "";
                String op = oRaw != null ? oRaw.toString() : "push";
                String valueType = tRaw != null ? tRaw.toString() : "plain";

                if (headerRaw.isBlank()) {
                    continue;
                }

                if ("liquid".equals(valueType)) {
                    result.append(String.format(
                            "        # Header '%s' uses liquid template — manual conversion required: %s%n",
                            headerRaw, value));
                    continue;
                }

                String headerLine = String.format(
                        "              - name: %s%n                value: \"%s\"%n", headerRaw, value);
                switch (op) {
                    case "add" -> addHeaders.append(headerLine);
                    case "delete" -> removeHeaders.append(
                            String.format("              - %s%n", headerRaw));
                    default -> setHeaders.append(headerLine);
                }
            }

            boolean hasAny = setHeaders.length() > 0
                    || addHeaders.length() > 0
                    || removeHeaders.length() > 0;
            if (!hasAny) {
                continue;
            }

            String filterType = "response".equals(direction)
                    ? "ResponseHeaderModifier" : "RequestHeaderModifier";
            String modifierKey = "response".equals(direction)
                    ? "responseHeaderModifier" : "requestHeaderModifier";

            StringBuilder modifier = new StringBuilder();
            if (setHeaders.length() > 0) {
                modifier.append("            set:\n").append(setHeaders);
            }
            if (addHeaders.length() > 0) {
                modifier.append("            add:\n").append(addHeaders);
            }
            if (removeHeaders.length() > 0) {
                modifier.append("            remove:\n").append(removeHeaders);
            }

            result.append(String.format(
                    "        - type: %s%n          %s:%n%s",
                    filterType, modifierKey, modifier));
        }

        return result.toString();
    }
}
