package com.redhat.migrationtoolkit.rhcl.service.generator.discovery;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test-only contributor proving CDI auto-discovery of {@link HttpRouteContributor} beans.
 */
@ApplicationScoped
@Priority(999)
public class TestMarkerHttpRouteContributor implements HttpRouteContributor {

    public static final String MARKER = "x-discovery-marker: rhcl-httproute-test";

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        if (RegistryDiscoveryMarkers.isDiscoveryService(ctx)) {
            builder.setDiscoveryMarker(MARKER);
        }
    }
}
