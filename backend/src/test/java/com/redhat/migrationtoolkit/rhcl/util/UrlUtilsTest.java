package com.redhat.migrationtoolkit.rhcl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "https://api.example.com:8443/path, api.example.com",
            "http://my-service:8080, my-service",
            "my-service.svc.cluster.local, my-service.svc.cluster.local"
    })
    void extractHostname_parsesHost(String url, String expectedHost) {
        assertEquals(expectedHost, UrlUtils.extractHostname(url));
    }

    @Test
    void extractHostname_blank_returnsNull() {
        assertNull(UrlUtils.extractHostname(""));
    }

    @Test
    void extractPort_withExplicitPort() {
        assertEquals(8443, UrlUtils.extractPort("https://api.example.com:8443", 443));
    }

    @Test
    void extractPort_defaultsWhenMissing() {
        assertEquals(443, UrlUtils.extractPort("https://api.example.com", 443));
    }

    @Test
    void extractInternalService_stripsSvcSuffix() {
        assertEquals("my-service", UrlUtils.extractInternalService("http://my-service:8080", "app"));
    }
}
