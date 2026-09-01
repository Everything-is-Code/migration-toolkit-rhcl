package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import com.redhat.migrationtoolkit.rhcl.service.conversion.UpstreamSupport;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPBackendRef;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatchBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetry;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Priority(360)
public class UpstreamContributor implements HttpRouteContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        Policy upstream = policyFinder().findEnabled(ctx.service, "upstream");
        if (upstream == null) {
            return;
        }
        if (UpstreamSupport.isGlobal(upstream)) {
            contributeGlobal(builder, ctx, upstream);
            return;
        }
        contributePathScoped(builder, ctx, upstream);
    }

    private void contributeGlobal(HttpRouteBuilder builder, ConversionContext ctx, Policy upstream) {
        String url = UpstreamSupport.globalUrl(upstream);
        ResolvedBackend override = UpstreamSupport.resolveOverrideBackend(ctx.serviceKebabName, url);
        if (override == null) {
            return;
        }
        builder.setOverrideBackends(List.of(override));
    }

    private void contributePathScoped(HttpRouteBuilder builder, ConversionContext ctx, Policy upstream) {
        List<UpstreamSupport.UpstreamRule> rules = UpstreamSupport.parseRules(upstream);
        HTTPRouteTimeouts timeouts = builder.timeouts();
        HTTPRouteRetry retry = builder.retry();
        List<HTTPRouteFilter> sharedFilters = builder.sharedFilters();

        for (UpstreamSupport.UpstreamRule rule : rules) {
            if (!rule.convertible()) {
                continue;
            }
            ResolvedBackend override = UpstreamSupport.resolveOverrideBackend(
                    ctx.serviceKebabName, rule.url());
            if (override == null) {
                continue;
            }
            List<ResolvedBackend> selected = List.of(override);
            UpstreamSupport.MatchApproximation match = rule.match();
            String pathValue = match.value();
            if (match.type() == UpstreamSupport.MatchType.PATH_PREFIX) {
                builder.addPathForOptions(pathValue);
            }

            String matchType = match.type() == UpstreamSupport.MatchType.PATH_PREFIX
                    ? "PathPrefix" : "RegularExpression";

            List<HTTPRouteFilter> filters = HttpRouteSupport.buildRuleFilters(selected, sharedFilters);
            List<HTTPBackendRef> backendRefs = HttpRouteSupport.buildBackendRefs(selected);

            var httpMatch = new HTTPRouteMatchBuilder()
                    .withNewPath()
                    .withType(matchType)
                    .withValue(pathValue)
                    .endPath()
                    .build();

            var ruleBuilder = new HTTPRouteRuleBuilder()
                    .withMatches(httpMatch)
                    .withBackendRefs(backendRefs);
            if (!filters.isEmpty()) ruleBuilder.withFilters(filters);
            if (timeouts != null) ruleBuilder.withTimeouts(timeouts);
            if (retry != null) ruleBuilder.withRetry(retry);
            builder.addRule(ruleBuilder.build());
        }
    }
}
