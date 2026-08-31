package com.redhat.migrationtoolkit.rhcl.service.conversion;

import io.fabric8.istio.api.networking.v1alpha3.ServiceEntryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IstioModelSmokeTest {

    @Test
    void serviceEntryBuilder_compilesAndBuilds() {
        var serviceEntry = new ServiceEntryBuilder()
                .withNewMetadata()
                .withName("demo-service-entry")
                .withNamespace("migration-ns")
                .endMetadata()
                .withNewSpec()
                .addToHosts("backend.internal.svc")
                .endSpec()
                .build();

        assertNotNull(serviceEntry.getMetadata());
        assertEquals("demo-service-entry", serviceEntry.getMetadata().getName());
        assertNotNull(serviceEntry.getSpec().getHosts());
        assertEquals("backend.internal.svc", serviceEntry.getSpec().getHosts().get(0));
    }
}
