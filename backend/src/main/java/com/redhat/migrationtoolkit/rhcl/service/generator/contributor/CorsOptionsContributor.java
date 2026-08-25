package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
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
        String timeoutsBlock = builder.timeoutsBlock();
        String retryBlock = builder.retryBlock();
        String sharedFilters = builder.sharedFilters();
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
            String filtersBlock = HttpRouteSupport.buildRuleFiltersBlock(selected, sharedFilters);
            builder.appendRule("""
    - matches:
        - path:
            type: PathPrefix
            value: "%s"
          method: OPTIONS
%s%s%s      backendRefs:
%s""".formatted(path, filtersBlock, timeoutsBlock, retryBlock,
                    HttpRouteSupport.formatBackendRefs(selected)));
        }
    }
}
