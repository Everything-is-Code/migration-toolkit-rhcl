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
        String timeoutsBlock = builder.timeoutsBlock();
        String retryBlock = builder.retryBlock();
        String sharedFilters = builder.sharedFilters();
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
                    emitRule(builder, List.of(op), selected, sharedFilters, timeoutsBlock, retryBlock);
                }
            } else {
                emitRule(builder, convertible, selected, sharedFilters, timeoutsBlock, retryBlock);
            }
        }
    }

    private static void emitRule(HttpRouteBuilder builder, List<MatchOp> ops,
            List<ResolvedBackend> selected, String sharedFilters, String timeoutsBlock,
            String retryBlock) {
        for (MatchOp op : ops) {
            if (op.kind() == MatchKind.PATH) {
                String path = HttpRouteSupport.toGatewayApiPathPrefix(op.value());
                builder.addPathForOptions(path);
            }
        }
        String matchBody = formatMatches(ops);
        if (matchBody.isBlank()) {
            return;
        }
        String filtersBlock = HttpRouteSupport.buildRuleFiltersBlock(selected, sharedFilters);
        builder.appendRule("""
    - matches:
        - %s
%s%s%s      backendRefs:
%s""".formatted(matchBody, filtersBlock, timeoutsBlock, retryBlock,
                HttpRouteSupport.formatBackendRefs(selected)));
    }

    static String formatMatches(List<MatchOp> ops) {
        List<String> headerItems = new ArrayList<>();
        List<String> queryItems = new ArrayList<>();
        String pathValue = null;
        for (MatchOp op : ops) {
            if (!op.convertible()) {
                continue;
            }
            switch (op.kind()) {
                case HEADER -> {
                    if (op.name() != null) {
                        headerItems.add("            - name: " + op.name()
                                + "\n              value: "
                                + HttpRouteSupport.yamlDoubleQuoted(op.value()));
                    }
                }
                case QUERY_ARG -> {
                    if (op.name() != null) {
                        queryItems.add("            - name: " + op.name()
                                + "\n              value: "
                                + HttpRouteSupport.yamlDoubleQuoted(op.value()));
                    }
                }
                case PATH -> pathValue = HttpRouteSupport.toGatewayApiPathPrefix(op.value());
                default -> {
                    // jwt / unsupported already filtered
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (!headerItems.isEmpty()) {
            sb.append("headers:\n");
            for (String item : headerItems) {
                sb.append(item).append('\n');
            }
            first = false;
        }
        if (!queryItems.isEmpty()) {
            if (!first) {
                sb.append("          ");
            }
            sb.append("queryParams:\n");
            for (String item : queryItems) {
                sb.append(item).append('\n');
            }
            first = false;
        }
        if (pathValue != null) {
            if (!first) {
                sb.append("          ");
            }
            sb.append("path:\n            type: PathPrefix\n            value: ")
                    .append(HttpRouteSupport.yamlDoubleQuoted(pathValue));
        }
        return sb.toString().stripTrailing();
    }
}
