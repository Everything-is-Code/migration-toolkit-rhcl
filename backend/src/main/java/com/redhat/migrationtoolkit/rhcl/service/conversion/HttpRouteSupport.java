package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared HTTPRoute path/backend/filter helpers (no ConversionService dependency). */
public final class HttpRouteSupport {

    private static final Logger LOG = Logger.getLogger(HttpRouteSupport.class);

    private HttpRouteSupport() {
    }

    /**
     * Convert a 3scale Mapping Rule pattern into a Gateway API PathPrefix value.
     */
    public static String toGatewayApiPathPrefix(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "/";
        }
        int braceIdx = pattern.indexOf('{');
        String prefix = braceIdx >= 0 ? pattern.substring(0, braceIdx) : pattern;
        if (prefix.length() > 1 && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix.isBlank() ? "/" : prefix;
    }

    public static List<ResolvedBackend> selectBackendsForPath(List<ResolvedBackend> backends, String rulePath) {
        if (backends == null || backends.isEmpty()) {
            return List.of();
        }
        String path = rulePath == null || rulePath.isBlank() ? "/" : rulePath;
        List<ResolvedBackend> matches = new ArrayList<>();
        int bestLen = -1;
        for (ResolvedBackend backend : backends) {
            if (!isMountPrefixOf(backend.mountPath, path)) {
                continue;
            }
            int len = backend.mountPath.length();
            if (len > bestLen) {
                matches.clear();
                matches.add(backend);
                bestLen = len;
            } else if (len == bestLen) {
                matches.add(backend);
            }
        }
        if (matches.isEmpty()) {
            LOG.warnf(
                    "No backend mount matched path %s; falling back to all %d backends",
                    path, backends.size());
            return List.copyOf(backends);
        }
        return matches;
    }

    public static boolean isMountPrefixOf(String mountPath, String rulePath) {
        String mount = BackendResolver.normalizeMountPath(mountPath);
        String path = rulePath == null || rulePath.isBlank() ? "/" : rulePath;
        if ("/".equals(mount)) {
            return true;
        }
        return path.equals(mount) || path.startsWith(mount.endsWith("/") ? mount : mount + "/");
    }

    public static String formatBackendRefs(List<ResolvedBackend> selected) {
        boolean weighted = selected.size() > 1;
        StringBuilder sb = new StringBuilder();
        for (ResolvedBackend backend : selected) {
            sb.append("        - name: ").append(backend.refName).append('\n');
            sb.append("          port: ").append(backend.port).append('\n');
            if (weighted) {
                int weight = backend.weight != null ? backend.weight : 1;
                sb.append("          weight: ").append(weight).append('\n');
            }
        }
        return sb.toString();
    }

    public static String uniqueExternalHost(List<ResolvedBackend> selected) {
        Set<String> hosts = selected.stream()
                .filter(b -> b.type == BackendType.EXTERNAL && b.externalHost != null)
                .map(b -> b.externalHost)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (hosts.size() > 1) {
            LOG.warnf(
                    "Skipping Host URLRewrite: %d distinct external hosts on one HTTPRoute rule (%s); "
                            + "Gateway API Host rewrite is rule-scoped, not per-backendRef",
                    hosts.size(), hosts);
            return null;
        }
        return hosts.size() == 1 ? hosts.iterator().next() : null;
    }

    public static String buildRuleFiltersBlock(List<ResolvedBackend> selected, String sharedFilters) {
        StringBuilder filterItems = new StringBuilder();
        String rewriteHost = uniqueExternalHost(selected);
        if (rewriteHost != null) {
            filterItems.append("""
        - type: URLRewrite
          urlRewrite:
            hostname: "%s"
""".formatted(rewriteHost));
        }
        filterItems.append(sharedFilters);
        return filterItems.length() > 0
                ? "      filters:\n" + filterItems
                : "";
    }

    /** Double-quote a YAML scalar so values like {@code *} do not parse as aliases. */
    public static String yamlDoubleQuoted(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static List<String> toStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split("[,\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public static boolean isHeaderModificationPolicy(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        return "headers".equals(n) || "header_modification".equals(n);
    }

    public static Policy findHeaderModificationPolicy(ApiService service) {
        if (service.policies == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && isHeaderModificationPolicy(p.name))
                .findFirst()
                .orElse(null);
    }

    public static void collectMappingRulePaths(ApiService service, LinkedHashSet<String> pathsForOptions) {
        if (service.mappingRules == null || service.mappingRules.isEmpty()) {
            pathsForOptions.add("/");
            return;
        }
        Set<String> catchAllMethods = new java.util.HashSet<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (MappingRule rule : service.mappingRules) {
            String path = toGatewayApiPathPrefix(rule.pattern);
            String method = rule.httpMethod != null ? rule.httpMethod : "GET";
            if (catchAllMethods.contains(method) || !emitted.add(path + " " + method)) {
                continue;
            }
            if ("/".equals(path)) {
                catchAllMethods.add(method);
            }
            pathsForOptions.add(path);
        }
    }
}
