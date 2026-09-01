package com.redhat.migrationtoolkit.rhcl.service.conversion;

import io.fabric8.istio.api.networking.v1alpha3.ServiceEntryBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.GatewayBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GatewayApiModelSmokeTest {

    @Test
    void gatewayAndHttpRouteBuilders_compileAndBuild() {
        var gateway = new GatewayBuilder()
                .withNewMetadata()
                .withName("demo-gateway")
                .withNamespace("migration-ns")
                .endMetadata()
                .withNewSpec()
                .withGatewayClassName("openshift-default")
                .endSpec()
                .build();

        var route = new HTTPRouteBuilder()
                .withNewMetadata()
                .withName("demo-route")
                .withNamespace("migration-ns")
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();

        assertNotNull(gateway.getMetadata());
        assertEquals("demo-gateway", gateway.getMetadata().getName());
        assertNotNull(route.getMetadata());
        assertEquals("demo-route", route.getMetadata().getName());
    }
}
