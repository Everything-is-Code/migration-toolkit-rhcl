package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
@Priority(600)
public class KeycloakRoleCheckContributor implements AuthPolicyContributor {

    private static final Logger LOG = Logger.getLogger(KeycloakRoleCheckContributor.class);

    @Inject
    PolicyFinder policyFinder;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (!builder.hasBase()) {
            return;
        }
        Policy keycloak = policyFinder().findEnabled(ctx.service, "keycloak_role_check");
        if (keycloak == null) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"jwt".equals(authType)) {
            LOG.warnf(
                    "keycloak_role_check enabled but authentication is '%s' (not jwt) — skipping role rule",
                    authType);
            return;
        }
        String namedRule = buildNamedRule(keycloak);
        builder.appendAuthorizationRule(namedRule);
    }

    private record KeycloakRolePattern(String selector, String operator, String value) {}

    @SuppressWarnings("unchecked")
    static String buildNamedRule(Policy policy) {
        if (policy == null || policy.configuration == null) {
            return "";
        }
        Map<String, Object> cfg = policy.configuration;
        String checkType = String.valueOf(cfg.getOrDefault("type", "whitelist")).trim().toLowerCase(Locale.ROOT);
        boolean blacklist = "blacklist".equals(checkType);
        String operator = blacklist ? "excl" : "incl";

        List<KeycloakRolePattern> patterns = new ArrayList<>();
        Object scopesRaw = cfg.get("scopes");
        if (!(scopesRaw instanceof List<?> scopes)) {
            return "";
        }
        for (Object scopeObj : scopes) {
            if (!(scopeObj instanceof Map<?, ?> scopeMap)) {
                continue;
            }
            Map<String, Object> scope = (Map<String, Object>) scopeMap;
            Object realmRolesRaw = scope.get("realm_roles");
            if (realmRolesRaw instanceof List<?> realmRoles) {
                for (Object roleObj : realmRoles) {
                    String roleName = extractRoleName(roleObj);
                    if (roleName != null) {
                        patterns.add(new KeycloakRolePattern(
                                "auth.identity.realm_access.roles", operator, roleName));
                    }
                }
            }
            Object clientRolesRaw = PolicyConfigSupport.firstNonNull(
                    scope.get("client_roles"), scope.get("resource_roles"));
            if (clientRolesRaw instanceof List<?> clientRoles) {
                for (Object clientObj : clientRoles) {
                    if (!(clientObj instanceof Map<?, ?> clientMap)) {
                        continue;
                    }
                    Map<String, Object> client = (Map<String, Object>) clientMap;
                    Object clientNameRaw = client.get("name");
                    if (clientNameRaw == null || clientNameRaw.toString().isBlank()) {
                        continue;
                    }
                    String clientName = clientNameRaw.toString().trim();
                    Object rolesRaw = client.get("roles");
                    if (rolesRaw instanceof List<?> roles) {
                        for (Object roleObj : roles) {
                            String roleName = extractRoleName(roleObj);
                            if (roleName != null) {
                                patterns.add(new KeycloakRolePattern(
                                        "auth.identity.resource_access." + clientName + ".roles",
                                        operator, roleName));
                            }
                        }
                    }
                }
            }
        }
        if (patterns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("      keycloak-role-check:\n");
        sb.append("        patternMatching:\n");
        sb.append("          patterns:\n");
        for (KeycloakRolePattern pattern : patterns) {
            sb.append("            - selector: ").append(pattern.selector()).append('\n');
            sb.append("              operator: ").append(pattern.operator()).append('\n');
            sb.append("              value: ").append(HttpRouteSupport.yamlDoubleQuoted(pattern.value())).append('\n');
        }
        return sb.toString();
    }

    private static String extractRoleName(Object roleObj) {
        if (roleObj instanceof Map<?, ?> roleMap) {
            Object name = roleMap.get("name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString().trim();
            }
            return null;
        }
        if (roleObj != null && !roleObj.toString().isBlank()) {
            return roleObj.toString().trim();
        }
        return null;
    }
}
