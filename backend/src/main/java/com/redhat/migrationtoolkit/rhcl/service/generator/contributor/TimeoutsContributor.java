package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(300)
public class TimeoutsContributor implements HttpRouteContributor {

    @Override
    public void contribute(HttpRouteBuilder builder, ConversionContext ctx) {
        builder.setTimeoutsBlock(buildTimeoutsBlock(ctx.service));
    }

    static String buildTimeoutsBlock(ApiService service) {
        if (service.policies == null) {
            return "";
        }
        return service.policies.stream()
                .filter(p -> "upstream_connection".equals(p.name))
                .filter(p -> Boolean.TRUE.equals(p.enabled))
                .filter(p -> p.configuration != null)
                .map(TimeoutsContributor::formatTimeoutsBlock)
                .filter(block -> !block.isEmpty())
                .findFirst()
                .orElse("");
    }

    private static String formatTimeoutsBlock(Policy policy) {
        Object connectRaw = policy.configuration.get("connect_timeout");
        Object sendRaw = policy.configuration.get("send_timeout");
        Object readRaw = policy.configuration.get("read_timeout");

        if (connectRaw == null && sendRaw == null && readRaw == null) {
            return "";
        }

        StringBuilder block = new StringBuilder("      timeouts:\n");
        if (readRaw != null) {
            block.append(String.format("        request: \"%ss\"  # read_timeout%n", readRaw));
        }
        if (connectRaw != null) {
            block.append(String.format("        backendRequest: \"%ss\"  # connect_timeout%n", connectRaw));
        }
        if (sendRaw != null) {
            block.append(String.format(
                    "        # send_timeout: %ss  (no direct Gateway API mapping — see annotations)%n", sendRaw));
        }
        return block.toString();
    }
}
