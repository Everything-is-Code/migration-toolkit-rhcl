package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionContextTest {

    @Test
    void build_nullOptions_usesDefaults() {
        ApiService service = ConversionSupportTestFixtures.apiService("Demo API");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));

        ConversionContext ctx = ConversionContext.build(
                service, "demo-ns", null, null, new BackendResolver());

        assertEquals("gateway", ctx.loggingTarget);
        assertEquals("httproute", ctx.anonymousTarget);
        assertEquals("authorizationPolicy", ctx.ipCheckMode);
        assertEquals("demo-api", ctx.serviceKebabName);
        assertEquals(BackendType.INTERNAL, ctx.primaryBackendType);
    }

    @Test
    void build_authPolicyOpaMode_normalizesIpCheckMode() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        ConversionOptions options = ConversionSupportTestFixtures.conversionOptions();
        options.ipCheckMode = "authPolicyOpa";

        ConversionContext ctx = ConversionContext.build(
                service, "demo-ns", null, options, new BackendResolver());

        assertEquals("authPolicyOpa", ctx.ipCheckMode);
    }

    @Test
    void build_multiBackendWithOverrideUrl_setsOverrideIgnored() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "a", "https://a.example.com", "/a"));
        service.backends.add(ConversionSupportTestFixtures.backend(
                "b", "https://b.example.com", "/b"));

        ConversionContext ctx = ConversionContext.build(
                service, "demo-ns", "https://override.example.com",
                ConversionSupportTestFixtures.conversionOptions(), new BackendResolver());

        assertTrue(ctx.overrideIgnored);
        assertEquals(2, ctx.resolvedBackends.size());
        assertEquals("/a", ctx.resolvedBackends.get(0).mountPath);
    }

    @Test
    void emitDnsPolicy_staticAndInstance_requireHostname() {
        ConversionOptions enabled = ConversionSupportTestFixtures.conversionOptions();
        enabled.includeDnsPolicy = true;
        enabled.dnsHostname = "api.example.com";

        ConversionOptions missingHostname = ConversionSupportTestFixtures.conversionOptions();
        missingHostname.includeDnsPolicy = true;
        missingHostname.dnsHostname = "  ";

        assertTrue(ConversionContext.emitDnsPolicy(enabled));
        assertFalse(ConversionContext.emitDnsPolicy(missingHostname));
        assertFalse(ConversionContext.emitDnsPolicy(null));

        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        ConversionContext ctx = ConversionContext.build(
                service, "demo-ns", null, enabled, new BackendResolver());
        assertTrue(ctx.emitDnsPolicy());
    }
}
