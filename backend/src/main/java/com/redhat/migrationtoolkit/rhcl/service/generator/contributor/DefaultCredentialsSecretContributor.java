package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(900)
public class DefaultCredentialsSecretContributor implements SecretContributor {

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (builder.hasSecret()) {
            return;
        }
        builder.beginOpaqueSecret(builder.name() + "-credentials");
        builder.addStringData("client-id", ConversionConstants.CREDENTIAL_PLACEHOLDER);
        builder.addStringData("client-secret", ConversionConstants.CREDENTIAL_PLACEHOLDER);
    }
}
