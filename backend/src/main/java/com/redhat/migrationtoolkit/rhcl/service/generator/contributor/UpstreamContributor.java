package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import com.redhat.migrationtoolkit.rhcl.service.conversion.UpstreamSupport;
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
        String timeoutsBlock = builder.timeoutsBlock();
        String retryBlock = builder.retryBlock();
        String sharedFilters = builder.sharedFilters();
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
            String filtersBlock = HttpRouteSupport.buildRuleFiltersBlock(selected, sharedFilters);
            String matchType = match.type() == UpstreamSupport.MatchType.PATH_PREFIX
                    ? "PathPrefix" : "RegularExpression";
            builder.appendRule("""
    - matches:
        - path:
            type: %s
            value: "%s"
%s%s%s      backendRefs:
%s""".formatted(matchType, pathValue, filtersBlock, timeoutsBlock, retryBlock,
                    HttpRouteSupport.formatBackendRefs(selected)));
        }
    }
}
