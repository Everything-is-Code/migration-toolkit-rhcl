package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(1300)
public class ContentLimitsEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Override
    public String outputKey() {
        return "envoyfilter-content-limits.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy contentLimits = policyFinder.findEnabledAny(
                ctx.service, true, "content_limits", "payload_limits");
        if (contentLimits == null) {
            return false;
        }
        Integer requestBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, true);
        return requestBytes != null && requestBytes > 0;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy contentLimits = policyFinder.findEnabledAny(
                ctx.service, true, "content_limits", "payload_limits");
        Integer requestBytes = policyConfigSupport.resolveContentLimitBytes(contentLimits, true);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-content-limits
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: content_limits
spec:
  workloadSelector:
    labels:
      gateway.networking.k8s.io/gateway-name: %s-gateway
  configPatches:
    - applyTo: HTTP_FILTER
      match:
        context: GATEWAY
        listener:
          filterChain:
            filter:
              name: "envoy.filters.network.http_connection_manager"
              subFilter:
                name: "envoy.filters.http.router"
      patch:
        operation: INSERT_BEFORE
        value:
          name: envoy.filters.http.buffer
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.filters.http.buffer.v3.Buffer
            max_request_bytes: %d
""".formatted(name, namespace, name, name, requestBytes);
    }

    void bindManual(PolicyFinder finder, PolicyConfigSupport support) {
        this.policyFinder = finder;
        this.policyConfigSupport = support;
    }
}
