package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.SecretSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(310)
public class ApiKeySecretContributor implements SecretContributor {

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (builder.hasSecret()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"apiKey".equals(authType)) {
            return;
        }
        String apiKey = SecretSupport.generateRandomHex(32);
        builder.beginOpaqueSecret(builder.name() + "-api-key");
        builder.addStringData("api_key", apiKey);
    }
}
