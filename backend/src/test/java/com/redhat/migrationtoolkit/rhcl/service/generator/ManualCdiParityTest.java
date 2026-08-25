package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretContributor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ensures Manual factory contributor lists match CDI-discovered beans.
 * Catches silent divergence when a contributor works in Quarkus but is
 * missing from Manual wiring (unit tests) or vice-versa.
 */
@QuarkusTest
class ManualCdiParityTest {

    @Inject
    Instance<AuthPolicyContributor> cdiAuthContributors;

    @Inject
    Instance<HttpRouteContributor> cdiHttpRouteContributors;

    @Inject
    Instance<SecretContributor> cdiSecretContributors;

    @Inject
    Instance<ResourceGenerator> cdiGenerators;

    @Test
    void authPolicyContributors_manualMatchesCdi() {
        Set<String> manual = ManualAuthPolicyContributorFactory.create().stream()
                .map(c -> c.getClass().getSimpleName())
                .collect(Collectors.toSet());
        Set<String> cdi = toClassNameSet(cdiAuthContributors);
        assertEquals(cdi, manual,
                "Manual AuthPolicy contributors diverge from CDI");
    }

    @Test
    void httpRouteContributors_manualMatchesCdi() {
        Set<String> manual = ManualHttpRouteContributorFactory.create().stream()
                .map(c -> c.getClass().getSimpleName())
                .collect(Collectors.toSet());
        Set<String> cdi = toClassNameSet(cdiHttpRouteContributors);
        assertEquals(cdi, manual,
                "Manual HttpRoute contributors diverge from CDI");
    }

    @Test
    void secretContributors_manualMatchesCdi() {
        Set<String> manual = ManualSecretContributorFactory.create().stream()
                .map(c -> c.getClass().getSimpleName())
                .collect(Collectors.toSet());
        Set<String> cdi = toClassNameSet(cdiSecretContributors);
        assertEquals(cdi, manual,
                "Manual Secret contributors diverge from CDI");
    }

    @Test
    void resourceGenerators_manualMatchesCdi() {
        Set<String> manual = ManualResourceGeneratorFactory.create().stream()
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.toSet());
        Set<String> cdi = toClassNameSet(cdiGenerators);
        assertEquals(cdi, manual,
                "Manual ResourceGenerator list diverges from CDI");
    }

    private static <T> Set<String> toClassNameSet(Instance<T> instance) {
        return StreamSupport.stream(instance.spliterator(), false)
                .map(bean -> bean.getClass().getSimpleName().replaceFirst("_ClientProxy$", ""))
                .filter(name -> !name.startsWith("TestMarker"))
                .collect(Collectors.toSet());
    }
}
