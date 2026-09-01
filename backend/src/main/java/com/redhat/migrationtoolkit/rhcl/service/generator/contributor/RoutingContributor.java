package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport.MatchKind;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport.MatchOp;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RoutingSupport.RoutingRule;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPBackendRef;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeaderMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeaderMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPQueryParamMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPQueryParamMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetry;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits conditional HTTPRoute rules from 3scale {@code routing} before mapping fallthrough (#150).
 */
@ApplicationScoped
@Priority(350)
public class RoutingContributor implements HttpRouteContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        Policy routing = policyFinder().findEnabled(ctx.service, "routing");
        if (routing == null) {
            return;
        }
        HTTPRouteTimeouts timeouts = builder.timeouts();
        HTTPRouteRetry retry = builder.retry();
        List<HTTPRouteFilter> sharedFilters = builder.sharedFilters();
        List<ResolvedBackend> productBackends = builder.effectiveBackends();

        for (RoutingRule rule : RoutingSupport.parseRules(routing)) {
            List<MatchOp> convertible = rule.convertibleOps();
            if (convertible.isEmpty()) {
                continue;
            }
            ResolvedBackend override = rule.url() != null && !rule.url().isBlank()
                    ? RoutingSupport.resolveBackendForUrl(ctx, rule.url())
                    : null;
            List<ResolvedBackend> selected = override != null
                    ? List.of(override)
                    : productBackends;
            if (selected == null || selected.isEmpty()) {
                continue;
            }

            if ("or".equals(rule.combineOp())) {
                for (MatchOp op : convertible) {
                    emitRule(builder, List.of(op), selected, sharedFilters, timeouts, retry);
                }
            } else {
                emitRule(builder, convertible, selected, sharedFilters, timeouts, retry);
            }
        }
    }

    private static void emitRule(HttpRouteBuilder builder, List<MatchOp> ops,
            List<ResolvedBackend> selected, List<HTTPRouteFilter> sharedFilters,
            HTTPRouteTimeouts timeouts, HTTPRouteRetry retry) {
        // Register paths for OPTIONS rules
        for (MatchOp op : ops) {
            if (op.kind() == MatchKind.PATH) {
                String path = HttpRouteSupport.toGatewayApiPathPrefix(op.value());
                builder.addPathForOptions(path);
            }
        }

        HTTPRouteMatch match = buildMatch(ops);
        if (match == null) {
            return;
        }

        List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(selected, sharedFilters);
        List<HTTPBackendRef> backendRefs = HttpRouteSupport.buildBackendRefs(selected);

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

    static HTTPRouteMatch buildMatch(List<MatchOp> ops) {
        List<HTTPHeaderMatch> headerMatches = new ArrayList<>();
        List<HTTPQueryParamMatch> queryMatches = new ArrayList<>();
        String pathValue = null;

        for (MatchOp op : ops) {
            if (!op.convertible()) {
                continue;
            }
            switch (op.kind()) {
                case HEADER -> {
                    if (op.name() != null) {
                        headerMatches.add(new HTTPHeaderMatchBuilder()
                                .withName(op.name())
                                .withValue(op.value())
                                .build());
                    }
                }
                case QUERY_ARG -> {
                    if (op.name() != null) {
                        queryMatches.add(new HTTPQueryParamMatchBuilder()
                                .withName(op.name())
                                .withValue(op.value())
                                .build());
                    }
                }
                case PATH -> pathValue = HttpRouteSupport.toGatewayApiPathPrefix(op.value());
                default -> {
                    // jwt / unsupported already filtered by convertible()
                }
            }
        }

        if (headerMatches.isEmpty() && queryMatches.isEmpty() && pathValue == null) {
            return null;
        }

        var matchBuilder = new HTTPRouteMatchBuilder();
        if (!headerMatches.isEmpty()) {
            matchBuilder.withHeaders(headerMatches);
        }
        if (!queryMatches.isEmpty()) {
            matchBuilder.withQueryParams(queryMatches);
        }
        if (pathValue != null) {
            matchBuilder.withNewPath()
                    .withType("PathPrefix")
                    .withValue(pathValue)
                    .endPath();
        }
        return matchBuilder.build();
    }
}
