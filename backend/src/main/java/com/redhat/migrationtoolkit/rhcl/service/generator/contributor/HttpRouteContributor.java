package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

/**
 * Contributes fragments to an HTTPRoute YAML document via {@link HttpRouteBuilder}.
 * Implementations are CDI beans discovered via {@code Instance<HttpRouteContributor>}.
 */
public interface HttpRouteContributor {

    /**
     * Contribute metadata annotations, shared filters, per-rule blocks, or full rules.
     * Use {@link jakarta.annotation.Priority} to control fragment order.
     */
    void contribute(HttpRouteBuilder builder, ConversionContext ctx);
}
