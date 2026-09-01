package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeader;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilterBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(200)
public class HeaderModContributor implements HttpRouteContributor {

    private static final Logger LOG = Logger.getLogger(HeaderModContributor.class);

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        for (HTTPRouteFilter filter : buildHeaderModificationFilters(ctx.service, builder)) {
            builder.addSharedFilter(filter);
        }
    }

    static List<HTTPRouteFilter> buildHeaderModificationFilters(ApiService service) {
        return buildHeaderModificationFilters(service, null);
    }

    @SuppressWarnings("unchecked")
    static List<HTTPRouteFilter> buildHeaderModificationFilters(ApiService service, HttpRouteBuilder builder) {
        Policy policy = HttpRouteSupport.findHeaderModificationPolicy(service);
        if (policy == null || policy.configuration == null) {
            return List.of();
        }

        List<HTTPRouteFilter> result = new ArrayList<>();

        for (String direction : new String[]{"response", "request"}) {
            Object raw = policy.configuration.get(direction);
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                continue;
            }

            List<HTTPHeader> setHeaders = new ArrayList<>();
            List<HTTPHeader> addHeaders = new ArrayList<>();
            List<String> removeHeaders = new ArrayList<>();

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object hRaw = entry.get("header");
                Object vRaw = entry.get("value");
                Object oRaw = entry.get("op");
                Object tRaw = entry.get("value_type");
                String headerName = (hRaw != null ? hRaw.toString() : "").replace(":", "").trim();
                String value = vRaw != null ? vRaw.toString() : "";
                String op = oRaw != null ? oRaw.toString() : "push";
                String valueType = tRaw != null ? tRaw.toString() : "plain";

                if (headerName.isBlank()) {
                    continue;
                }

                if ("liquid".equals(valueType)) {
                    String note = String.format(
                            "Header '%s' uses liquid template — manual conversion required: %s",
                            headerName, value);
                    if (builder != null) {
                        builder.addYamlComment(note);
                    }
                    LOG.infof("%s", note);
                    continue;
                }

                HTTPHeader header = new HTTPHeaderBuilder()
                        .withName(headerName)
                        .withValue(value)
                        .build();

                switch (op) {
                    case "add" -> addHeaders.add(header);
                    case "delete" -> removeHeaders.add(headerName);
                    default -> setHeaders.add(header);
                }
            }

            boolean hasAny = !setHeaders.isEmpty() || !addHeaders.isEmpty() || !removeHeaders.isEmpty();
            if (!hasAny) {
                continue;
            }

            boolean isResponse = "response".equals(direction);
            String filterType = isResponse ? "ResponseHeaderModifier" : "RequestHeaderModifier";

            var modifierNested = new HTTPRouteFilterBuilder().withType(filterType);
            if (isResponse) {
                var mod = modifierNested.withNewResponseHeaderModifier();
                if (!setHeaders.isEmpty()) {
                    mod.withSet(setHeaders);
                }
                if (!addHeaders.isEmpty()) {
                    mod.withAdd(addHeaders);
                }
                if (!removeHeaders.isEmpty()) {
                    mod.withRemove(removeHeaders);
                }
                result.add(mod.endResponseHeaderModifier().build());
            } else {
                var mod = modifierNested.withNewRequestHeaderModifier();
                if (!setHeaders.isEmpty()) {
                    mod.withSet(setHeaders);
                }
                if (!addHeaders.isEmpty()) {
                    mod.withAdd(addHeaders);
                }
                if (!removeHeaders.isEmpty()) {
                    mod.withRemove(removeHeaders);
                }
                result.add(mod.endRequestHeaderModifier().build());
            }
        }

        return result;
    }
}
