package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(700)
public class IpCheckOpaContributor implements AuthPolicyContributor {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    PolicyFinder policyFinder() {
        return policyFinder != null ? policyFinder : new PolicyFinder();
    }

    PolicyConfigSupport policyConfigSupport() {
        return policyConfigSupport != null ? policyConfigSupport : new PolicyConfigSupport();
    }

    @Override
    public void contribute(AuthPolicyBuilder builder, ConversionContext ctx) {
        if (!builder.hasBase() || !"authPolicyOpa".equals(ctx.ipCheckMode)) {
            return;
        }
        Policy ipCheck = policyFinder().findEnabled(ctx.service, "ip_check");
        if (ipCheck == null) {
            return;
        }
        String opaBlock = buildIpCheckOpaAuthorization(ipCheck, policyConfigSupport());
        builder.appendAuthorizationRule(opaBlock);
    }

    static String buildIpCheckOpaAuthorization(Policy ipCheck, PolicyConfigSupport policyConfigSupport) {
        Map<String, Object> cfg = ipCheck.configuration != null ? ipCheck.configuration : Map.of();
        String checkType = String.valueOf(cfg.getOrDefault("check_type", "whitelist"));
        List<String> ips = HttpRouteSupport.toStringList(cfg.get("ips"));
        if (ips.isEmpty()) {
            return "";
        }
        StringBuilder cidrList = new StringBuilder();
        for (String ip : ips) {
            String cidr = policyConfigSupport.normalizeCidr(ip);
            if (cidr == null) {
                continue;
            }
            if (cidrList.length() > 0) {
                cidrList.append(", ");
            }
            cidrList.append("\"").append(cidr).append("\"");
        }
        if (cidrList.length() == 0) {
            return "";
        }
        boolean whitelist = !"blacklist".equalsIgnoreCase(checkType)
                && !"deny".equalsIgnoreCase(checkType);
        String allowBody = whitelist
                ? """
            allow {
              some i
              net.cidr_contains(cidrs[i], client_ip)
            }
"""
                : """
            allow {
              not denied
            }
            denied {
              some i
              net.cidr_contains(cidrs[i], client_ip)
            }
""";
        return """
    authorization:
      ip-check:
        opa:
          rego: |
            package ipcheck
            import future.keywords
            cidrs := [%s]
            # WARNING: peer connection IP under Authorino; for end-client IP allowlists prefer AuthorizationPolicy (remoteIpBlocks).
            client_ip := input.source.address
%s""".formatted(cidrList, allowBody);
    }
}
