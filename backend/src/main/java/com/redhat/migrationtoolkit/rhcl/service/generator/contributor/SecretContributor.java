package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

/**
 * Contributes fragments to a Secret YAML document via {@link SecretBuilder}.
 */
public interface SecretContributor {

    void contribute(SecretBuilder builder, ConversionContext ctx);
}
