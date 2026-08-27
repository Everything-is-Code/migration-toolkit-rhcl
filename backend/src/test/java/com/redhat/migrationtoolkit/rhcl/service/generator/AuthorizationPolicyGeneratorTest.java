package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthorizationPolicyGeneratorTest {

    @Inject
    AuthorizationPolicyGenerator generator;

    @Test
    void applies_returnsTrue_whenIpCheckPolicyPresent() {
        Policy ipCheck = GeneratorTestSupport.policyWithConfig("ip_check", Map.of(
                "check_type", "whitelist",
                "ips", List.of("203.0.113.10")));
        ApiService service = GeneratorTestSupport.basicService("IP API", "ip-api");
        service.policies = List.of(ipCheck);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        assertTrue(generator.applies(ctx));
    }

    @Test
    void applies_returnsFalse_whenIpCheckModeIsAuthPolicyOpa() {
        Policy ipCheck = GeneratorTestSupport.policyWithConfig("ip_check", Map.of(
                "check_type", "whitelist",
                "ips", List.of("203.0.113.10")));
        ApiService service = GeneratorTestSupport.basicService("IP API", "ip-api");
        service.policies = List.of(ipCheck);
        ConversionOptions options = new ConversionOptions();
        options.ipCheckMode = "authPolicyOpa";
        ConversionContext ctx = GeneratorTestSupport.context(service, options);

        assertFalse(generator.applies(ctx));
    }

    @Test
    void generate_producesAuthorizationPolicyWithRemoteIpBlocks() {
        Policy ipCheck = GeneratorTestSupport.policyWithConfig("ip_check", Map.of(
                "check_type", "whitelist",
                "ips", List.of("203.0.113.10")));
        ApiService service = GeneratorTestSupport.basicService("IP API", "ip-api");
        service.policies = List.of(ipCheck);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertNotNull(yaml);
        assertEquals("authorizationpolicy.yaml", generator.outputKey());
        assertTrue(yaml.contains("apiVersion: security.istio.io/v1"));
        assertTrue(yaml.contains("kind: AuthorizationPolicy"));
        assertTrue(yaml.contains("name: ip-api-ip-check"));
        assertTrue(yaml.contains("namespace: " + GeneratorTestSupport.NAMESPACE));
        assertTrue(yaml.contains("action: ALLOW"));
        assertTrue(yaml.contains("remoteIpBlocks:"));
        assertTrue(yaml.contains("203.0.113.10/32"));
        assertTrue(yaml.contains("remoteIpBlocks:\n              - \"203.0.113.10/32\""),
                "CIDR list must be nested under remoteIpBlocks");
    }

    @Test
    void generate_blacklistCheckType_usesDenyAction() {
        Policy ipCheck = GeneratorTestSupport.policyWithConfig("ip_check", Map.of(
                "check_type", "blacklist",
                "ips", List.of("10.0.0.1")));
        ApiService service = GeneratorTestSupport.basicService("IP API", "ip-api");
        service.policies = List.of(ipCheck);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);

        assertTrue(yaml.contains("action: DENY"));
    }
}
