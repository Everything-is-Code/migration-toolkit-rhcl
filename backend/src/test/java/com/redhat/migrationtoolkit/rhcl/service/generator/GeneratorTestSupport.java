package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared fixtures for {@link ResourceGenerator} unit tests (#210). */
final class GeneratorTestSupport {

    static final String NAMESPACE = "test-ns";

    private GeneratorTestSupport() {
    }

    static ApiService basicService(String name, String systemName) {
        ApiService service = new ApiService();
        service.id = "42";
        service.name = name;
        service.systemName = systemName;
        service.description = "Test API migrated from 3scale";
        return service;
    }

    static ApiService serviceWithExternalBackend(String systemName, String externalUrl) {
        ApiService service = basicService(systemName, systemName);
        Backend backend = new Backend();
        backend.name = "default";
        backend.systemName = "default";
        backend.privateEndpoint = externalUrl;
        service.backends = List.of(backend);
        return service;
    }

    static ApiService serviceWithMappingRules(String systemName) {
        ApiService service = basicService(systemName, systemName);
        MappingRule rule = new MappingRule();
        rule.httpMethod = "GET";
        rule.pattern = "/api/v1";
        service.mappingRules = List.of(rule);
        return service;
    }

    static ConversionContext context(ApiService service) {
        return context(service, NAMESPACE, new ConversionOptions());
    }

    static ConversionContext context(ApiService service, ConversionOptions options) {
        return context(service, NAMESPACE, options);
    }

    static ConversionContext context(ApiService service, String namespace, ConversionOptions options) {
        return ConversionContext.build(service, namespace, null, options, new BackendResolver());
    }

    static Policy enabledPolicy(String name) {
        Policy policy = new Policy();
        policy.name = name;
        policy.enabled = true;
        policy.configuration = new HashMap<>();
        return policy;
    }

    static Policy policyWithConfig(String name, Map<String, Object> configuration) {
        Policy policy = enabledPolicy(name);
        policy.configuration = new HashMap<>(configuration);
        return policy;
    }

    static Authentication apiKeyAuth() {
        Authentication auth = new Authentication();
        auth.type = "apiKey";
        auth.location = "header";
        auth.paramName = "user_key";
        return auth;
    }
}
