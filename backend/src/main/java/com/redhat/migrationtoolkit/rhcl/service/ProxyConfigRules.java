package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PublicHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3scale production proxy config is the source of truth for mapping rules.
 * {@code /proxy/mapping_rules.json} is often empty even when the proxy is published.
 */
public final class ProxyConfigRules {

    private ProxyConfigRules() {
    }

    public static List<MappingRule> fromProxyConfig(Map<String, Object> config, List<Backend> backends) {
        List<Map<String, Object>> rawRules = proxyRules(config);
        Map<String, String> usageByBackend = new LinkedHashMap<>();
        if (backends != null) {
            for (Backend backend : backends) {
                if (backend.id != null && backend.path != null) {
                    usageByBackend.put(backend.id, backend.path);
                }
            }
        }
        List<MappingRule> rules = new ArrayList<>();
        for (Map<String, Object> item : rawRules) {
            Object nested = item.get("proxy_rule");
            Map<String, Object> rule = nested instanceof Map<?, ?> map ? cast(map) : item;
            String ownerType = stringValue(rule.get("owner_type"));
            String pattern = stringValue(rule.get("pattern"));
            if ("Proxy".equals(ownerType) && (pattern == null || pattern.isBlank()
                    || "/".equals(pattern) || "$".equals(pattern))) {
                continue;
            }
            pattern = PublicHost.stripUsagePrefix(pattern, usageByBackend.get(stringValue(rule.get("owner_id"))));
            MappingRule mapped = new MappingRule();
            mapped.httpMethod = stringValue(rule.get("http_method"));
            mapped.pattern = pattern;
            mapped.metricSystemName = stringValue(rule.get("metric_system_name"));
            rules.add(mapped);
        }
        return rules;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> proxyRules(Map<String, Object> config) {
        if (config == null) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> proxyConfig = (Map<String, Object>) config.get("proxy_config");
            Map<String, Object> content = (Map<String, Object>) proxyConfig.get("content");
            Map<String, Object> proxy = (Map<String, Object>) content.get("proxy");
            Object rules = proxy.get("proxy_rules");
            if (!(rules instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> typed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    typed.add(cast(map));
                }
            }
            return typed;
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
