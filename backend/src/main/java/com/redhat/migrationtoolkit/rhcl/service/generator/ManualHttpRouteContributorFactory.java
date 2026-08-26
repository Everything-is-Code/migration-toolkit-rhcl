package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.CorsFiltersContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.CorsOptionsContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HeaderModContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteAnnotationsContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.MappingRulesContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.RetryContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.TimeoutsContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.UpstreamContributor;

import java.util.ArrayList;
import java.util.List;

final class ManualHttpRouteContributorFactory {

    private ManualHttpRouteContributorFactory() {
    }

    static List<HttpRouteContributor> create() {
        List<HttpRouteContributor> contributors = new ArrayList<>();
        contributors.add(new HttpRouteAnnotationsContributor());
        contributors.add(new HeaderModContributor());
        contributors.add(new CorsFiltersContributor());
        contributors.add(new TimeoutsContributor());
        contributors.add(new RetryContributor());
        contributors.add(new UpstreamContributor());
        contributors.add(new MappingRulesContributor());
        contributors.add(new CorsOptionsContributor());
        return contributors;
    }
}
