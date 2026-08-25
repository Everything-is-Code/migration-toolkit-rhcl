package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

/**
 * Contributes fragments to an AuthPolicy YAML document via {@link AuthPolicyBuilder}.
 */
public interface AuthPolicyContributor {

    void contribute(AuthPolicyBuilder builder, ConversionContext ctx);
}
