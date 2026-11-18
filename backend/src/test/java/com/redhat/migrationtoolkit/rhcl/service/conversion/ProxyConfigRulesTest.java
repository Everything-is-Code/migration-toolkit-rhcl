package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigRulesTest {

    @Test
    void fromProxyConfig_stripsUsagePrefixAndSkipsProxyCatchAll() {
        Backend accounts = new Backend();
        accounts.id = "7";
        accounts.path = "/customers";

        Map<String, Object> config = Map.of("proxy_config", Map.of(
                "content", Map.of(
                        "proxy", Map.of(
                                "proxy_rules", List.of(
                                        Map.of("proxy_rule", Map.of(
                                                "owner_type", "Proxy",
                                                "pattern", "/",
                                                "http_method", "GET")),
                                        Map.of("proxy_rule", Map.of(
                                                "owner_type", "Backend",
                                                "owner_id", 7,
                                                "pattern", "/customers/api/v1/customers",
                                                "http_method", "GET",
                                                "metric_system_name", "hits")))))));

        List<MappingRule> rules = ProxyConfigRules.fromProxyConfig(config, List.of(accounts));

        assertEquals(1, rules.size());
        assertEquals("GET", rules.get(0).httpMethod);
        assertEquals("/api/v1/customers", rules.get(0).pattern);
        assertEquals("hits", rules.get(0).metricSystemName);
    }

    @Test
    void fromProxyConfig_missingTree_returnsEmpty() {
        assertTrue(ProxyConfigRules.fromProxyConfig(Map.of(), List.of()).isEmpty());
        assertTrue(ProxyConfigRules.fromProxyConfig(null, null).isEmpty());
        assertTrue(ProxyConfigRules.proxyRules(Map.of("proxy_config", "broken")).isEmpty());
        assertTrue(ProxyConfigRules.proxyRules(Map.of(
                "proxy_config", Map.of(
                        "content", Map.of(
                                "proxy", Map.of("proxy_rules", "none"))))).isEmpty());
    }

    @Test
    void fromProxyConfig_keepsFlatRulesAndSkipsBlankProxyPatterns() {
        Backend withoutId = new Backend();
        withoutId.path = "/ignored";
        Map<String, Object> config = Map.of("proxy_config", Map.of(
                "content", Map.of(
                        "proxy", Map.of(
                                "proxy_rules", List.of(
                                        "skip-me",
                                        Map.of(
                                                "owner_type", "Proxy",
                                                "http_method", "GET"),
                                        Map.of(
                                                "owner_type", "Proxy",
                                                "pattern", "$",
                                                "http_method", "GET"),
                                        Map.of(
                                                "owner_type", "Proxy",
                                                "pattern", "  ",
                                                "http_method", "POST"),
                                        Map.of(
                                                "owner_type", "Proxy",
                                                "pattern", "/status",
                                                "http_method", "GET"),
                                        Map.of(
                                                "owner_type", "Backend",
                                                "pattern", "/api/v1/transactions",
                                                "http_method", "PUT"))))));

        Backend noPath = new Backend();
        noPath.id = "9";
        List<MappingRule> rules = ProxyConfigRules.fromProxyConfig(config, List.of(withoutId, noPath));

        assertEquals(2, rules.size());
        assertEquals("/status", rules.get(0).pattern);
        assertEquals("PUT", rules.get(1).httpMethod);
        assertEquals("/api/v1/transactions", rules.get(1).pattern);
        assertNull(rules.get(1).metricSystemName);
    }
}
