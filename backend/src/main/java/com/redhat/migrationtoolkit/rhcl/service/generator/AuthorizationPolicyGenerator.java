package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import io.fabric8.istio.api.security.v1.AuthorizationPolicy;
import io.fabric8.istio.api.api.security.v1beta1.AuthorizationPolicyAction;
import io.fabric8.istio.api.security.v1.AuthorizationPolicyBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(1500)
public class AuthorizationPolicyGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    PolicyConfigSupport policyConfigSupport;

    @Inject
    ManifestSerializer manifestSerializer;

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
        AuthorizationPolicyAction policyAction = deny
                ? AuthorizationPolicyAction.DENY
                : AuthorizationPolicyAction.ALLOW;

        List<String> remoteIpBlocks = new ArrayList<>();
        for (String ip : ips) {
            String cidr = policyConfigSupport.normalizeCidr(ip);
            if (cidr != null) {
                remoteIpBlocks.add(cidr);
            }
        }
        if (remoteIpBlocks.isEmpty()) {
            remoteIpBlocks.add("0.0.0.0/0");
        }

        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;

        AuthorizationPolicy authorizationPolicy = new AuthorizationPolicyBuilder()
                .withApiVersion("security.istio.io/v1")
                .withKind("AuthorizationPolicy")
                .withNewMetadata()
                .withName(name + "-ip-check")
                .withNamespace(namespace)
                .withLabels(IstioManifestSupport.baseLabels(name, ctx.includeMigratedFromLabel))
                .addToAnnotations("3scale-migration/ip-check-type", checkType)
                .endMetadata()
                .withNewSpec()
                .withAction(policyAction)
                .addNewRule()
                .addNewFrom()
                .withNewSource()
                .withRemoteIpBlocks(remoteIpBlocks)
                .endSource()
                .endFrom()
                .endRule()
                .endSpec()
                .build();

        return serializer().toYaml(authorizationPolicy);
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

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
