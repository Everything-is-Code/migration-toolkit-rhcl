package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorOrdering;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Priority(200)
public class HttpRouteGenerator implements ResourceGenerator {

    @Inject
    Instance<HttpRouteContributor> contributors;

    private List<HttpRouteContributor> manualContributors;

    void bindManualContributors(List<HttpRouteContributor> list) {
        this.manualContributors = list;
    }

    @Override
    public String outputKey() {
        return "httproute.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        HttpRouteBuilder builder = new HttpRouteBuilder(ctx);
        for (HttpRouteContributor contributor : orderedContributors()) {
            contributor.contribute(builder, ctx);
        }
        return builder.build();
    }

    private List<HttpRouteContributor> orderedContributors() {
        List<HttpRouteContributor> list = manualContributors != null
                ? new ArrayList<>(manualContributors)
                : new ArrayList<>();
        if (manualContributors == null && contributors != null) {
            contributors.forEach(list::add);
        }
        list.sort(Comparator.comparingInt(ContributorOrdering::priorityOf));
        return list;
    }
}
