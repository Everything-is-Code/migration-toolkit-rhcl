package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.MappingRulesContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.RoutingContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.UpstreamContributor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualHttpRouteContributorFactoryTest {

    @Test
    void create_includesRoutingThenUpstreamBeforeMappingRules() {
        List<HttpRouteContributor> contributors = ManualHttpRouteContributorFactory.create();
        int routing = indexOf(contributors, RoutingContributor.class);
        int upstream = indexOf(contributors, UpstreamContributor.class);
        int mapping = indexOf(contributors, MappingRulesContributor.class);
        assertTrue(routing >= 0, "RoutingContributor must be registered");
        assertTrue(upstream >= 0, "UpstreamContributor must be registered");
        assertTrue(mapping >= 0, "MappingRulesContributor must be registered");
        assertTrue(routing < upstream, "Routing must run before Upstream");
        assertTrue(upstream < mapping, "Upstream must run before MappingRules");
    }

    private static int indexOf(List<HttpRouteContributor> contributors, Class<?> type) {
        for (int i = 0; i < contributors.size(); i++) {
            if (type.isInstance(contributors.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
