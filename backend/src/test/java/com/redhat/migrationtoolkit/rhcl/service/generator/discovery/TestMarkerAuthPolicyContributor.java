package com.redhat.migrationtoolkit.rhcl.service.generator.discovery;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(999)
public class TestMarkerAuthPolicyContributor implements AuthPolicyContributor {

    public static final String MARKER = "x-discovery-marker: rhcl-authpolicy-test";

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (RegistryDiscoveryMarkers.isDiscoveryService(ctx)) {
            builder.setDiscoveryMarker(MARKER);
        }
    }
}
