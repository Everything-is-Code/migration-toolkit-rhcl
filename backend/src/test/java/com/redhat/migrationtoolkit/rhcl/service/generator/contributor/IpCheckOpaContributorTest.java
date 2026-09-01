package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthorizationRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpCheckOpaContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.ipCheckPolicy("whitelist",
                List.of("192.168.1.0/24")));
        ConversionOptions options = new ConversionOptions();
        options.ipCheckMode = "authPolicyOpa";
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new IpCheckOpaContributor().contribute(builder, ctx);

        assertTrue(SERIALIZER.toYaml(builder.build()).contains("ip-check:"));
    }

    @Test
    void shouldContribute_false_whenNotOpaMode() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.ipCheckPolicy("whitelist",
                List.of("10.0.0.1")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new IpCheckOpaContributor().contribute(builder, ctx);

        assertFalse(SERIALIZER.toYaml(builder.build()).contains("ip-check:"));
    }

    @Test
    void contribute_viaContributor_opaMode() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.ipCheckPolicy("whitelist",
                List.of("10.0.0.0/8")));
        ConversionOptions options = new ConversionOptions();
        options.ipCheckMode = "authPolicyOpa";
        ConversionContext ctx = ContributorTestFixtures.context(service, options);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilderWithBase(ctx);

        new IpCheckOpaContributor().contribute(builder, ctx);

        assertTrue(SERIALIZER.toYaml(builder.build()).contains("package ipcheck"));
    }

    @Test
    void buildIpCheckOpaAuthorization_emptyIps_returnsNull() {
        Policy policy = ContributorTestFixtures.ipCheckPolicy("whitelist", List.of());
        assertNull(IpCheckOpaContributor.buildIpCheckOpaAuthorization(policy, new PolicyConfigSupport()));
    }

    @Test
    void buildIpCheckOpaAuthorization_denyCheckType() {
        Policy policy = ContributorTestFixtures.ipCheckPolicy("deny", List.of("1.2.3.4"));
        AuthorizationRule rule = IpCheckOpaContributor.buildIpCheckOpaAuthorization(
                policy, new PolicyConfigSupport());
        assertNotNull(rule);
        String rego = rule.value().get("opa").toString();
        assertTrue(rego.contains("denied"));
    }

    @Test
    void contribute_addsExpectedFragments_whitelist() {
        Policy policy = ContributorTestFixtures.ipCheckPolicy("whitelist",
                List.of("10.0.0.0/8", "192.168.0.1"));
        AuthorizationRule rule = IpCheckOpaContributor.buildIpCheckOpaAuthorization(
                policy, new PolicyConfigSupport());

        assertNotNull(rule);
        String ruleStr = rule.value().toString();
        assertTrue(ruleStr.contains("package ipcheck"));
        assertTrue(ruleStr.contains("net.cidr_contains"));
        assertTrue(ruleStr.contains("\"10.0.0.0/8\""));
    }

    @Test
    void contribute_addsExpectedFragments_blacklist() {
        Policy policy = ContributorTestFixtures.ipCheckPolicy("blacklist", List.of("172.16.0.0/12"));
        AuthorizationRule rule = IpCheckOpaContributor.buildIpCheckOpaAuthorization(
                policy, new PolicyConfigSupport());

        assertNotNull(rule);
        String ruleStr = rule.value().toString();
        assertTrue(ruleStr.contains("denied"));
    }
}
