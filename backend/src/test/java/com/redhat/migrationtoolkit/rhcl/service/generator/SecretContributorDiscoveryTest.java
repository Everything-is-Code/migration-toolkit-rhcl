package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.discovery.TestMarkerSecretContributor;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SecretContributorDiscoveryTest {

    @Inject
    SecretGenerator secretGenerator;

    @Test
    void discoveryMarkerContributor_isAutoApplied() {
        ApiService service = new ApiService();
        service.name = RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME;
        service.systemName = RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME;

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        String yaml = secretGenerator.generate(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) YamlAssertions.parse(yaml).get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, String> annotations = (Map<String, String>) metadata.get("annotations");

        assertEquals("rhcl-secret-test", annotations.get("x-discovery-marker"));
    }

    @Test
    void discoveryMarker_absentForNormalService() {
        ApiService service = new ApiService();
        service.name = "my-api";
        service.systemName = "my-api";

        ConversionContext ctx = ConversionContext.build(
                service, "test-ns", null, new ConversionOptions(), new BackendResolver());

        String yaml = secretGenerator.generate(ctx);

        assertFalse(yaml.contains(TestMarkerSecretContributor.MARKER));
    }
}
