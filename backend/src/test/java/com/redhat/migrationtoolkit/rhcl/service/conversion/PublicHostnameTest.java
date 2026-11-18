package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicHostnameTest {

    @Test
    void publicHostname_dnsPolicyWinsOverTheIssuer() {
        ApiService service = new ApiService();
        service.name = "Neural Bank";
        service.systemName = "neuralbank";
        Authentication auth = new Authentication();
        auth.oidcIssuerEndpoint = "https://rhbk.apps.example.com/realms/nb";
        service.authentication = auth;

        ConversionOptions options = new ConversionOptions();
        options.includeDnsPolicy = true;
        options.dnsHostname = "custom.example.com";

        ConversionContext ctx = ConversionContext.build(service, "business-apis", null, options, new BackendResolver());

        assertEquals("custom.example.com", ctx.publicHostname());
        assertEquals("custom.example.com", ctx.routeHostname());
    }

    @Test
    void publicHostname_withoutAuthentication_returnsNull() {
        ApiService service = new ApiService();
        service.name = "Demo";
        service.systemName = "demo";

        ConversionContext ctx = ConversionContext.build(
                service, "business-apis", null, new ConversionOptions(), new BackendResolver());

        assertNull(ctx.publicHostname());
    }
}
