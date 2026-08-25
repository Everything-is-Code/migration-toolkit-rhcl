package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ReadmeSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(1900)
public class ReadmeGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Inject
    RateLimitSupport rateLimitSupport;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    PolicyConfigSupport policyConfigSupport() {
        return policyConfigSupport != null ? policyConfigSupport : new PolicyConfigSupport();
    }

    RateLimitSupport rateLimitSupport() {
        return rateLimitSupport != null ? rateLimitSupport : RateLimitSupport.forManual();
    }

    @Override
    public String outputKey() {
        return "README.md";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        ConversionService.ReadmeNotes readmeNotes = new ConversionService.ReadmeNotes();
        return ReadmeSupport.build(
                ctx,
                readmeNotes,
                policyFinder(),
                policyConfigSupport(),
                rateLimitSupport());
    }
}
