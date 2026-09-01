package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPBackendRef;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetry;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CORS preflight: OPTIONS on product path(s) when cors policy is enabled.
 */
@ApplicationScoped
@Priority(500)
public class CorsOptionsContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        if (!builder.corsEnabled()) {
            return;
        }
        ApiService service = ctx.service;
        HTTPRouteTimeouts timeouts = builder.timeouts();
        HTTPRouteRetry retry = builder.retry();
        List<HTTPRouteFilter> sharedFilters = builder.sharedFilters();
        List<ResolvedBackend> backends = builder.backends();

        Set<String> emittedOptions = new HashSet<>();
        if (service.mappingRules != null) {
            for (MappingRule rule : service.mappingRules) {
                if (rule.httpMethod != null && "OPTIONS".equalsIgnoreCase(rule.httpMethod)) {
                    emittedOptions.add(HttpRouteSupport.toGatewayApiPathPrefix(rule.pattern));
                }
            }
        }

        for (String path : builder.pathsForOptions()) {
            if (!emittedOptions.add(path)) {
                continue;
            }
            List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, path);
            List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(selected, sharedFilters);
            List<HTTPBackendRef> backendRefs = HttpRouteSupport.buildBackendRefs(selected);

            var match = new HTTPRouteMatchBuilder()
                    .withNewPath()
                    .withType("PathPrefix")
                    .withValue(path)
                    .endPath()
                    .withMethod("OPTIONS")
                    .build();

            var ruleBuilder = new HTTPRouteRuleBuilder()
                    .withMatches(match)
                    .withBackendRefs(backendRefs);
            if (!filters.isEmpty()) ruleBuilder.withFilters(filters);
            if (timeouts != null) ruleBuilder.withTimeouts(timeouts);
            if (retry != null) ruleBuilder.withRetry(retry);
            builder.addRule(ruleBuilder.build());
        }
    }
}
