package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(100)
public class HttpRouteAnnotationsContributor implements HttpRouteContributor {

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
        ApiService service = ctx.service;
        String upstream = buildUpstreamAnnotations(service);
        if (!upstream.isBlank()) {
            builder.appendAnnotationBody(upstream);
        }
        Policy contentLimits = policyFinder().findEnabledAny(
                service, true, "content_limits", "payload_limits");
        if (contentLimits != null) {
            Integer responseBytes = policyConfigSupport().resolveContentLimitBytes(contentLimits, false);
            if (responseBytes != null && responseBytes > 0) {
                builder.appendAnnotationBody(String.format(
                        "    3scale-migration/response-content-limit: \"%d\"%n", responseBytes));
            }
        }
    }

    private String buildUpstreamAnnotations(ApiService service) {
        if (service.policies == null) {
            return "";
        }
        Object sendTimeout = null;
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
            Object sendRaw = p.configuration.get("send_timeout");
            if (sendRaw != null) {
                sendTimeout = sendRaw;
                break;
            }
        }
        if (sendTimeout == null) {
            return "";
        }
        return """
    3scale-migration/upstream-send-timeout: "%ss"
""".formatted(sendTimeout);
    }
}
