package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeoutsBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(300)
public class TimeoutsContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        builder.setTimeouts(buildTimeouts(ctx.service));
    }

    /**
     * Build typed {@link HTTPRouteTimeouts} from the {@code upstream_connection} policy.
     * Returns {@code null} when no relevant timeout values are present.
     * Note: {@code send_timeout} has no direct Gateway API mapping and is recorded
     * as an annotation by {@link HttpRouteAnnotationsContributor} instead.
     */
    static HTTPRouteTimeouts buildTimeouts(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> "upstream_connection".equals(p.name))
                .filter(p -> Boolean.TRUE.equals(p.enabled))
                .filter(p -> p.configuration != null)
                .map(TimeoutsContributor::toTimeouts)
                .filter(t -> t != null)
                .findFirst()
                .orElse(null);
    }

    private static HTTPRouteTimeouts toTimeouts(Policy policy) {
        Object connectRaw = policy.configuration.get("connect_timeout");
        Object readRaw = policy.configuration.get("read_timeout");

        if (connectRaw == null && readRaw == null) {
            return null;
        }

        var builder = new HTTPRouteTimeoutsBuilder();
        if (readRaw != null) {
            builder.withRequest(readRaw + "s");
        }
        if (connectRaw != null) {
            builder.withBackendRequest(connectRaw + "s");
        }
        return builder.build();
    }
}
