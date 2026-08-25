package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(1400)
public class RetryEnvoyFilterGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Override
    public String outputKey() {
        return "envoyfilter-retry.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        if (ctx.options.retriesSupported) {
            return false;
        }
        Integer retries = policyConfigSupport.resolveRetryAttempts(
                policyFinder.findEnabled(ctx.service, "retry"));
        return retries != null && retries > 0;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Integer retries = policyConfigSupport.resolveRetryAttempts(
                policyFinder.findEnabled(ctx.service, "retry"));
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        return """
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: %s-retry
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/source: retry
    3scale-migration/note: "HTTPRoute retry unsupported on this cluster — Envoy route retry_policy fallback"
spec:
  workloadSelector:
    labels:
      gateway.networking.k8s.io/gateway-name: %s-gateway
  configPatches:
    - applyTo: HTTP_ROUTE
      match:
        context: GATEWAY
      patch:
        operation: MERGE
        value:
          route:
            retry_policy:
              retry_on: "5xx,reset,connect-failure,refused-stream"
              num_retries: %d
""".formatted(name, namespace, name, name, retries);
    }

    void bindManual(PolicyFinder finder, PolicyConfigSupport support) {
        this.policyFinder = finder;
        this.policyConfigSupport = support;
    }
}
