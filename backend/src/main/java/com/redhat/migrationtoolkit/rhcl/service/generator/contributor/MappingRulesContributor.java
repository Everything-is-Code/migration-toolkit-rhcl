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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Priority(400)
public class MappingRulesContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        ApiService service = ctx.service;
        HTTPRouteTimeouts timeouts = builder.timeouts();
        HTTPRouteRetry retry = builder.retry();
        List<HTTPRouteFilter> sharedFilters = builder.sharedFilters();
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
                List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(selected, sharedFilters);
                List<HTTPBackendRef> backendRefs = HttpRouteSupport.buildBackendRefs(selected);

                var match = new HTTPRouteMatchBuilder()
                        .withNewPath()
                        .withType("PathPrefix")
                        .withValue(path)
                        .endPath()
                        .withMethod(method)
                        .build();

                var ruleBuilder = new HTTPRouteRuleBuilder()
                        .withMatches(match)
                        .withBackendRefs(backendRefs);
                if (!filters.isEmpty()) {
                    ruleBuilder.withFilters(filters);
                }
                if (timeouts != null) {
                    ruleBuilder.withTimeouts(timeouts);
                }
                if (retry != null) {
                    ruleBuilder.withRetry(retry);
                }
                builder.addRule(ruleBuilder.build());
            }
        } else {
            builder.addPathForOptions("/");
            List<ResolvedBackend> selected = HttpRouteSupport.selectBackendsForPath(backends, "/");
            List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(selected, sharedFilters);
            List<HTTPBackendRef> backendRefs = HttpRouteSupport.buildBackendRefs(selected);

            var match = new HTTPRouteMatchBuilder()
                    .withNewPath()
                    .withType("PathPrefix")
                    .withValue("/")
                    .endPath()
                    .build();

            var ruleBuilder = new HTTPRouteRuleBuilder()
                    .withMatches(match)
                    .withBackendRefs(backendRefs);
            if (!filters.isEmpty()) {
                ruleBuilder.withFilters(filters);
            }
            if (timeouts != null) {
                ruleBuilder.withTimeouts(timeouts);
            }
            if (retry != null) {
                ruleBuilder.withRetry(retry);
            }
            builder.addRule(ruleBuilder.build());
        }
    }
}
