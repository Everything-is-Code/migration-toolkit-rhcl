package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPolicyBuilderTest {

    @Test
    void build_assemblesBaseAndAuthorizationRules() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        AuthPolicyBuilder builder = new AuthPolicyBuilder(ctx);
        builder.setBaseYaml("""
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
  namespace: ns
  labels:
    app: demo-api
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: demo-api-route
  rules:
    authentication: {}
""");
        builder.appendAuthorizationRule("""
      jwt-claim-check:
        patternMatching:
          patterns:
            - selector: auth.identity.sub
              operator: eq
              value: "user"
""");

        String yaml = builder.build();

        assertTrue(yaml.contains("name: demo-api-auth"));
        assertTrue(yaml.contains("jwt-claim-check:"));
        assertTrue(yaml.contains("auth.identity.sub"));
    }
}
