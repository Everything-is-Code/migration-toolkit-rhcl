package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.TargetRef;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(400)
public class EmptyAuthenticationContributor implements AuthPolicyContributor {

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (builder.hasBase()) {
            return;
        }
        builder.setTargetRef(new TargetRef("gateway.networking.k8s.io", "HTTPRoute",
                builder.name() + "-route"));
        builder.setEmptyAuthentication();
    }
}
