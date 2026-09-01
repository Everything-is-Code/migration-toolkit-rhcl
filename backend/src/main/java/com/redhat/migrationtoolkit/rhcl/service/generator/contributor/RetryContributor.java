package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetry;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetryBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(310)
public class RetryContributor implements HttpRouteContributor {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    PolicyConfigSupport policyConfigSupport() {
        return policyConfigSupport != null ? policyConfigSupport : new PolicyConfigSupport();
    }

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        if (!ctx.options.retriesSupported) {
            builder.setRetry(null);
            return;
        }
        Integer attempts = policyConfigSupport().resolveRetryAttempts(
                policyFinder().findEnabled(ctx.service, "retry"));
        if (attempts == null || attempts <= 0) {
            builder.setRetry(null);
            return;
        }
        HTTPRouteRetry retry = new HTTPRouteRetryBuilder()
                .withAttempts(attempts)
                .build();
        builder.setRetry(retry);
    }
}
