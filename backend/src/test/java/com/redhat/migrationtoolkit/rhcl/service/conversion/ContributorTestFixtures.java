package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.HttpRouteBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared builders for contributor unit tests. */
public final class ContributorTestFixtures {

    public static final String NAMESPACE = "test-ns";

    private ContributorTestFixtures() {
    }

    public static ApiService apiService() {
        ApiService service = new ApiService();
        service.id = "1";
        service.name = "demo-api";
        service.systemName = "demo-api";
        service.backends = new ArrayList<>();
        service.policies = new ArrayList<>();
        service.mappingRules = new ArrayList<>();
        service.applicationPlans = new ArrayList<>();
        service.applications = new ArrayList<>();
        service.backends.add(backend("primary", "http://api.example.com:8080", "/"));
        return service;
    }

    public static ApiService apiServiceWithAuth(String authType) {
        ApiService service = apiService();
        if (authType != null) {
            service.authentication = new Authentication();
            service.authentication.type = authType;
            if ("jwt".equals(authType)) {
                service.authentication.oidcIssuerEndpoint = "https://idp.example.com/realms/demo";
            }
        }
        return service;
    }

    public static Backend backend(String name, String endpoint, String path) {
        Backend backend = new Backend();
        backend.name = name;
        backend.systemName = name;
        backend.privateEndpoint = endpoint;
        backend.path = path;
        return backend;
    }

    public static ConversionContext context(ApiService service) {
        return ConversionContext.build(service, NAMESPACE, null, new ConversionOptions(), new BackendResolver());
    }

    public static ConversionContext context(ApiService service, ConversionOptions options) {
        return ConversionContext.build(service, NAMESPACE, null, options, new BackendResolver());
    }

    public static Policy policy(String name, boolean enabled, Map<String, Object> configuration) {
        Policy policy = new Policy();
        policy.name = name;
        policy.enabled = enabled;
        policy.configuration = configuration != null ? configuration : new HashMap<>();
        return policy;
    }

    public static MappingRule mappingRule(String method, String pattern) {
        MappingRule rule = new MappingRule();
        rule.httpMethod = method;
        rule.pattern = pattern;
        return rule;
    }

    public static Application application(String appId, String... keys) {
        Application app = new Application();
        app.appId = appId;
        app.keys = List.of(keys);
        return app;
    }

    public static Policy anonymousAccessPolicy(String authType, Map<String, Object> extra) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("auth_type", authType);
        if (extra != null) {
            cfg.putAll(extra);
        }
        return policy("anonymous_access", true, cfg);
    }

    public static Policy corsPolicy(Map<String, Object> config) {
        return policy("cors", true, config);
    }

    public static Policy upstreamConnectionPolicy(int connect, int send, int read) {
        Map<String, Object> cfg = new HashMap<>();
        if (connect > 0) {
            cfg.put("connect_timeout", connect);
        }
        if (send > 0) {
            cfg.put("send_timeout", send);
        }
        if (read > 0) {
            cfg.put("read_timeout", read);
        }
        return policy("upstream_connection", true, cfg);
    }

    public static Policy headerModificationPolicy(List<Map<String, Object>> request,
            List<Map<String, Object>> response) {
        Map<String, Object> cfg = new HashMap<>();
        if (request != null) {
            cfg.put("request", request);
        }
        if (response != null) {
            cfg.put("response", response);
        }
        return policy("headers", true, cfg);
    }

    public static Policy keycloakRoleCheckPolicy(List<Map<String, Object>> scopes) {
        return policy("keycloak_role_check", true, Map.of("type", "whitelist", "scopes", scopes));
    }

    public static Policy ipCheckPolicy(String checkType, List<String> ips) {
        return policy("ip_check", true, Map.of("check_type", checkType, "ips", ips));
    }

    public static Policy jwtClaimCheckPolicy(List<Map<String, Object>> operations) {
        return policy("jwt_claim_check", true, Map.of(
                "enable_extended_context", true,
                "rules", List.of(Map.of(
                        "combine_op", "and",
                        "resource_type", "plain",
                        "resource", "/",
                        "methods", List.of("ANY"),
                        "operations", operations))));
    }

    public static Policy tokenIntrospectionPolicy(String url) {
        return policy("token_introspection", true, Map.of(
                "introspection_url", url,
                "client_id", "cid",
                "client_secret", "secret"));
    }

    public static Policy retryPolicy(int retries) {
        return policy("retry", true, Map.of("retries", retries));
    }

    public static AuthPolicyBuilder authPolicyBuilder(ConversionContext ctx) {
        return new AuthPolicyBuilder(ctx);
    }

    public static AuthPolicyBuilder authPolicyBuilderWithBase(ConversionContext ctx) {
        AuthPolicyBuilder builder = new AuthPolicyBuilder(ctx);
        builder.setBaseYaml("""
apiVersion: kuadrant.io/v1
kind: AuthPolicy
metadata:
  name: demo-api-auth
  namespace: test-ns
  labels:
    app: demo-api
    migrated-from: 3scale
spec:
  targetRef:
    group: gateway.networking.k8s.io
    kind: HTTPRoute
    name: demo-api-route
  rules:
    authentication:
      jwt-auth:
        jwt:
          issuerUrl: https://idp.example.com
""");
        return builder;
    }

    public static HttpRouteBuilder httpRouteBuilder(ConversionContext ctx) {
        return new HttpRouteBuilder(ctx);
    }

    public static SecretBuilder secretBuilder(ConversionContext ctx) {
        return new SecretBuilder(ctx);
    }
}
