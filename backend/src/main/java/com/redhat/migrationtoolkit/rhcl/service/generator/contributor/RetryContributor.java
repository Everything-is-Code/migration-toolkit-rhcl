package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
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
            builder.setRetryBlock("");
            return;
        }
        Integer attempts = policyConfigSupport().resolveRetryAttempts(
                policyFinder().findEnabled(ctx.service, "retry"));
        if (attempts == null || attempts <= 0) {
            builder.setRetryBlock("");
            return;
        }
        builder.setRetryBlock("""
      retry:
        attempts: %d
""".formatted(attempts));
    }
}
