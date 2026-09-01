package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakRoleCheckContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("jwt");
        service.policies.add(ContributorTestFixtures.keycloakRoleCheckPolicy(List.of(
                Map.of("realm_roles", List.of("admin")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new KeycloakRoleCheckContributor().contribute(builder, ctx);

        assertTrue(SERIALIZER.toYaml(builder.build()).contains("keycloak-role-check:"));
    }

    @Test
    void shouldContribute_false_whenNotJwt() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        service.policies.add(ContributorTestFixtures.keycloakRoleCheckPolicy(List.of(
                Map.of("realm_roles", List.of("admin")))));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new KeycloakRoleCheckContributor().contribute(builder, ctx);

        assertFalse(SERIALIZER.toYaml(builder.build()).contains("keycloak-role-check:"));
    }

    @Test
    void buildNamedRule_blacklist_usesExclOperator() {
        Policy policy = ContributorTestFixtures.policy("keycloak_role_check", true,
                Map.of("type", "blacklist", "scopes", List.of(
                        Map.of("realm_roles", List.of("banned")))));
        AuthorizationRule rule = KeycloakRoleCheckContributor.buildNamedRule(policy);
        assertNotNull(rule);
        String yaml = SERIALIZER.toYaml(rule);
        assertTrue(yaml.contains("operator: excl"));
    }

    @Test
    void buildNamedRule_clientResourceRolesAlias() {
        Policy policy = ContributorTestFixtures.policy("keycloak_role_check", true, Map.of(
                "scopes", List.of(Map.of(
                        "resource_roles", List.of(Map.of(
                                "name", "svc",
                                "roles", List.of("write")))))));
        AuthorizationRule rule = KeycloakRoleCheckContributor.buildNamedRule(policy);
        assertNotNull(rule);
        String yaml = SERIALIZER.toYaml(rule);
        assertTrue(yaml.contains("auth.identity.resource_access.svc.roles"));
    }

    @Test
    void buildNamedRule_emptyScopes_returnsNull() {
        assertNull(KeycloakRoleCheckContributor.buildNamedRule(
                ContributorTestFixtures.policy("keycloak_role_check", true, Map.of())));
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy policy = ContributorTestFixtures.keycloakRoleCheckPolicy(List.of(
                Map.of("realm_roles", List.of(Map.of("name", "editor"))),
                Map.of("client_roles", List.of(Map.of(
                        "name", "my-client",
                        "roles", List.of("read"))))));
        AuthorizationRule rule = KeycloakRoleCheckContributor.buildNamedRule(policy);
        assertNotNull(rule);
        String yaml = SERIALIZER.toYaml(rule);

        assertTrue(yaml.contains("auth.identity.realm_access.roles"));
        assertTrue(yaml.contains("operator: incl"));
        assertTrue(yaml.contains("value: editor"));
        assertTrue(yaml.contains("auth.identity.resource_access.my-client.roles"));
    }
}
