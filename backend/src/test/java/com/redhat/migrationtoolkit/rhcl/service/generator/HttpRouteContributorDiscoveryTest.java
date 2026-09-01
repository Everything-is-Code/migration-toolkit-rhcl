package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.discovery.TestMarkerHttpRouteContributor;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class HttpRouteContributorDiscoveryTest {

    @Inject
    HttpRouteGenerator httpRouteGenerator;

    @Test
    void discoveryMarkerContributor_isAutoApplied() {
        ApiService service = new ApiService();
        service.name = RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME;
        service.systemName = RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME;

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        String yaml = httpRouteGenerator.generate(ctx);

        // Fabric8 serializes annotation values with double quotes; check key and value separately
        assertTrue(yaml.contains("x-discovery-marker") && yaml.contains("rhcl-httproute-test"));
    }

    @Test
    void discoveryMarker_absentForNormalService() {
        ApiService service = new ApiService();
        service.name = "my-api";
        service.systemName = "my-api";

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        String yaml = httpRouteGenerator.generate(ctx);

        assertFalse(yaml.contains(TestMarkerHttpRouteContributor.MARKER));
    }
}
