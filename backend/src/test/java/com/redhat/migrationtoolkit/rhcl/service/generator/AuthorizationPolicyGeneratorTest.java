package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertNotNull(yaml);
        assertEquals("authorizationpolicy.yaml", generator.outputKey());
        assertEquals("security.istio.io/v1", parsed.get("apiVersion"));
        assertEquals("AuthorizationPolicy", parsed.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        assertEquals("ip-api-ip-check", metadata.get("name"));
        assertEquals(GeneratorTestSupport.NAMESPACE, metadata.get("namespace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("ALLOW", spec.get("action"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) spec.get("rules");
        assertNotNull(rules);
        assertFalse(rules.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> from = (List<Map<String, Object>>) rules.get(0).get("from");
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) from.get(0).get("source");
        @SuppressWarnings("unchecked")
        List<String> remoteIpBlocks = (List<String>) source.get("remoteIpBlocks");
        assertNotNull(remoteIpBlocks);
        assertTrue(remoteIpBlocks.contains("203.0.113.10/32"),
                "CIDR 203.0.113.10/32 must be in remoteIpBlocks");
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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) parsed.get("spec");
        assertEquals("DENY", spec.get("action"));
    }

    @Test
    void generate_nonListIps_defaultsToOpenCidr() {
        Policy ipCheck = GeneratorTestSupport.policyWithConfig("ip_check", Map.of(
                "check_type", "whitelist",
                "ips", "not-a-list"));
        ApiService service = GeneratorTestSupport.basicService("IP API", "ip-api");
        service.policies = List.of(ipCheck);
        ConversionContext ctx = GeneratorTestSupport.context(service);

        String yaml = generator.generate(ctx);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) ((Map<String, Object>) parsed.get("spec")).get("rules");
        @SuppressWarnings("unchecked")
        List<String> remoteIpBlocks = (List<String>) ((Map<String, Object>) ((List<Map<String, Object>>) rules.get(0).get("from")).get(0).get("source")).get("remoteIpBlocks");
        assertEquals(List.of("0.0.0.0/0"), remoteIpBlocks);
    }
}
