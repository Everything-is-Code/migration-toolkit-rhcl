package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1500)
public class AuthorizationPolicyGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Override
    public String outputKey() {
        return "authorizationpolicy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        Policy ipCheck = policyFinder.findEnabled(ctx.service, "ip_check");
        return ipCheck != null && "authorizationPolicy".equals(ctx.ipCheckMode);
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy ipCheck = policyFinder.findEnabled(ctx.service, "ip_check");
        Map<String, Object> cfg = ipCheck.configuration != null ? ipCheck.configuration : Map.of();
        String checkType = String.valueOf(cfg.getOrDefault("check_type", "whitelist"));
        List<String> ips = toStringList(cfg.get("ips"));
        boolean deny = "blacklist".equalsIgnoreCase(checkType) || "deny".equalsIgnoreCase(checkType);
        String action = deny ? "DENY" : "ALLOW";
        StringBuilder remoteIps = new StringBuilder();
        for (String ip : ips) {
            String cidr = policyConfigSupport.normalizeCidr(ip);
            if (cidr == null) {
                continue;
            }
            remoteIps.append("        - \"").append(cidr).append("\"\n");
        }
        if (remoteIps.length() == 0) {
            remoteIps.append("        - \"0.0.0.0/0\"\n");
        }
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        return """
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: %s-ip-check
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
  annotations:
    3scale-migration/ip-check-type: "%s"
spec:
  action: %s
  rules:
    - from:
        - source:
            remoteIpBlocks:
%s""".formatted(name, namespace, name, checkType, action, remoteIps);
    }

    void bindManual(PolicyFinder finder, PolicyConfigSupport support) {
        this.policyFinder = finder;
        this.policyConfigSupport = support;
    }

  @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of();
    }
}
