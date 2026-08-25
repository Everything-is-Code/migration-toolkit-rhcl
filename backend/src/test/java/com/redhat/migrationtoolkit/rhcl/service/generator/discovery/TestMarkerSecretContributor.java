package com.redhat.migrationtoolkit.rhcl.service.generator.discovery;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(999)
public class TestMarkerSecretContributor implements SecretContributor {

    public static final String MARKER = "x-discovery-marker: rhcl-secret-test";

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (RegistryDiscoveryMarkers.isDiscoveryService(ctx)) {
            builder.setDiscoveryMarker(MARKER);
        }
    }
}
