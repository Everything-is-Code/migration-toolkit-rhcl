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
        for (Policy p : service.policies) {
            if (!"upstream_connection".equals(p.name)) {
                continue;
            }
            if (!Boolean.TRUE.equals(p.enabled)) {
                continue;
            }
            if (p.configuration == null) {
                continue;
            }

            Object connectRaw = p.configuration.get("connect_timeout");
            Object sendRaw = p.configuration.get("send_timeout");
            Object readRaw = p.configuration.get("read_timeout");

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
        return "";
    }
}
