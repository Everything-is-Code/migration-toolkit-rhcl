package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthPolicyGeneratorTest {

    @Inject
    AuthPolicyGenerator generator;

    @Test
    void applies_returnsTrue_forStandardService() {
        ApiService service = GeneratorTestSupport.basicService("Auth API", "auth-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void generate_producesAuthPolicyWithTargetRef() {
        ApiService service = GeneratorTestSupport.basicService("Auth API", "auth-api");
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("policy.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: kuadrant.io/v1"));
        assertTrue(yaml.contains("kind: AuthPolicy"));
        assertTrue(yaml.contains("name: auth-api-auth"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("targetRef:"));
        assertTrue(yaml.contains("kind: HTTPRoute"));
        assertTrue(yaml.contains("name: auth-api-route"));
        assertTrue(yaml.contains("authentication:"));
    }
}
