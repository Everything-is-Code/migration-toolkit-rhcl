package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.JwtClaimCheckSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(500)
public class JwtClaimCheckContributor implements AuthPolicyContributor {

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (!builder.hasBase()) {
            return;
        }
        Policy claimCheck = policyFinder().findEnabled(ctx.service, "jwt_claim_check");
        if (claimCheck == null) {
            return;
        }
        JwtClaimCheckSupport.JwtClaimParseResult parsed = JwtClaimCheckSupport.parseRules(claimCheck);
        String namedRule = JwtClaimCheckSupport.buildNamedRule(parsed.patterns());
        builder.appendAuthorizationRule(namedRule);
    }
}
