package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryDiscoveryMarkersTest {

    @Test
    void isDiscoveryService_matchesMarkerSystemName() {
        ApiService markerService = ConversionSupportTestFixtures.apiService("ignored");
        markerService.systemName = RegistryDiscoveryMarkers.DISCOVERY_SYSTEM_NAME;
        ConversionContext markerCtx = ConversionSupportTestFixtures.context(markerService, "demo-ns");

        ApiService regularService = ConversionSupportTestFixtures.apiService("demo-api");
        ConversionContext regularCtx = ConversionSupportTestFixtures.context(regularService, "demo-ns");

        assertTrue(RegistryDiscoveryMarkers.isDiscoveryService(markerCtx));
        assertFalse(RegistryDiscoveryMarkers.isDiscoveryService(regularCtx));
    }
}
