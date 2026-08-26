package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPolicyYamlMergerTest {

    private static final String BASE_YAML = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
spec:
  rules:
    authentication:
      jwt-auth: {}
""";

    @Test
    void shouldContribute_true_mergesIntoExistingAuthorization() {
        String rule = """
      jwt-claim-check:
        patternMatching:
          patterns:
            - selector: auth.identity.sub
              operator: eq
              value: "alice"
""";
        String merged = AuthPolicyYamlMerger.mergeAuthorizationNamedRules(BASE_YAML, rule);

        assertTrue(merged.contains("jwt-claim-check:"));
        assertTrue(merged.contains("auth.identity.sub"));
    }

    @Test
    void shouldContribute_false_returnsUnchangedOnBlankInput() {
        assertEquals(BASE_YAML, AuthPolicyYamlMerger.mergeAuthorizationNamedRules(BASE_YAML, ""));
        assertEquals(BASE_YAML, AuthPolicyYamlMerger.mergeAuthorizationNamedRules(BASE_YAML, null));
        assertEquals("", AuthPolicyYamlMerger.mergeAuthorizationNamedRules("", "rule"));
    }

    @Test
    void merge_appendsAtEndWhenNoNewlineAfterAuthorization() {
        String yaml = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
spec:
  rules:
    authentication: {}
    authorization:""";
        String merged = AuthPolicyYamlMerger.mergeAuthorizationNamedRules(yaml, """
      test-rule:
        value: "x"
""");
        assertTrue(merged.contains("test-rule:"));
    }

    @Test
    void contribute_addsExpectedFragments_createsAuthorizationWhenMissing() {
        String withoutAuth = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
spec:
  rules:
    authentication:
      jwt-auth: {}
""".replace("\n    authorization:", "\n    # no authorization yet");

        String rule = """
      keycloak-role-check:
        patternMatching:
          patterns:
            - selector: auth.identity.roles
              operator: incl
              value: "admin"
""";
        String yamlNoAuth = """
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
spec:
  rules:
    authentication:
      jwt-auth: {}
""";
        String merged = AuthPolicyYamlMerger.mergeAuthorizationNamedRules(yamlNoAuth, rule);

        assertTrue(merged.contains("authorization:"));
        assertTrue(merged.contains("keycloak-role-check:"));
    }

    @Test
    void merge_stripsAuthorizationPrefixFromBlock() {
        String prefixed = """
    authorization:
      ip-check:
        opa:
          rego: |
            package ipcheck
""";
        String merged = AuthPolicyYamlMerger.mergeAuthorizationNamedRules(BASE_YAML, prefixed);

        assertTrue(merged.contains("ip-check:"));
        assertTrue(merged.indexOf("authorization:") < merged.indexOf("ip-check:"));
    }
}
