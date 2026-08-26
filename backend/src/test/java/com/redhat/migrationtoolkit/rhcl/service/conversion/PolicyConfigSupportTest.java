package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolicyConfigSupportTest {

    private PolicyConfigSupport support;

    @BeforeEach
    void setUp() {
        support = new PolicyConfigSupport();
    }

    @Test
    void resolveRetryAttempts_validNumber_returnsInt() {
        Policy policy = ConversionSupportTestFixtures.policy("retry", true,
                Map.of("retries", 3));

        assertEquals(3, support.resolveRetryAttempts(policy));
    }

    @Test
    void resolveRetryAttempts_missingConfig_returnsNull() {
        assertNull(support.resolveRetryAttempts(null));
        assertNull(support.resolveRetryAttempts(new Policy()));
    }

    @Test
    void resolveContentLimitBytes_requestAndResponseKeys() {
        Policy policy = ConversionSupportTestFixtures.policy("content_limits", true, Map.of(
                "request", 1024,
                "response_content_limit", 2048));

        assertEquals(1024, support.resolveContentLimitBytes(policy, true));
        assertEquals(2048, support.resolveContentLimitBytes(policy, false));
    }

    @Test
    void normalizeCidr_ipv4AndIpv6_addsPrefixLength() {
        assertEquals("10.0.0.1/32", support.normalizeCidr("10.0.0.1"));
        assertEquals("2001:db8::1/128", support.normalizeCidr("2001:db8::1"));
        assertEquals("192.168.0.0/24", support.normalizeCidr("192.168.0.0/24"));
    }

    @Test
    void normalizeCidr_blank_returnsNull() {
        assertNull(support.normalizeCidr(null));
        assertNull(support.normalizeCidr("   "));
    }

    @Test
    void firstNonNull_prefersFirstNonNullValue() {
        assertEquals("a", PolicyConfigSupport.firstNonNull("a", "b"));
        assertEquals("b", PolicyConfigSupport.firstNonNull(null, "b"));
        assertNull(PolicyConfigSupport.firstNonNull(null, null));
    }
}
