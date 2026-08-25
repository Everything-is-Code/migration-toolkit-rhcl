package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

/**
 * Produces one output file entry in the conversion result map.
 */
public interface ResourceGenerator {

    /** Map key (e.g. {@code gateway.yaml}). */
    String outputKey();

    /** Whether this generator should run for the given conversion context. */
    boolean applies(ConversionContext ctx);

    /** YAML or README content; {@code null} or blank skips insertion. */
    String generate(ConversionContext ctx);
}
