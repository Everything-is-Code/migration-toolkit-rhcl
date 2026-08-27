package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Priority(400)
public class MappingRulesContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        ApiService service = ctx.service;
        String timeoutsBlock = builder.timeoutsBlock();
        String retryBlock = builder.retryBlock();
        String sharedFilters = builder.sharedFilters();
        List<ResolvedBackend> backends = builder.effectiveBackends();

        if (service.mappingRules != null && !service.mappingRules.isEmpty()) {
            Set<String> catchAllMethods = new HashSet<>();
            Set<String> emitted = new LinkedHashSet<>();
            for (MappingRule rule : service.mappingRules) {
                String path = HttpRouteSupport.toGatewayApiPathPrefix(rule.pattern);
                String method = rule.httpMethod != null ? rule.httpMethod : "GET";

                if (catchAllMethods.contains(method) || !emitted.add(path + " " + method)) {
                    continue;
                }
                if ("/".equals(path)) {
                    catchAllMethods.add(method);
                }
                builder.addPathForOptions(path);

                List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, path);
                String filtersBlock = HttpRouteSupport.buildRuleFiltersBlock(selected, sharedFilters);
                builder.appendRule("""
    - matches:
        - path:
            type: PathPrefix
            value: "%s"
          method: %s
%s%s%s      backendRefs:
%s""".formatted(path, method, filtersBlock, timeoutsBlock, retryBlock,
                        HttpRouteSupport.formatBackendRefs(selected)));
            }
        } else {
            builder.addPathForOptions("/");
            List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, "/");
            String filtersBlock = HttpRouteSupport.buildRuleFiltersBlock(selected, sharedFilters);
            builder.appendRule("""
    - matches:
        - path:
            type: PathPrefix
            value: "/"
%s%s%s      backendRefs:
%s""".formatted(filtersBlock, timeoutsBlock, retryBlock,
                    HttpRouteSupport.formatBackendRefs(selected)));
        }
    }
}
