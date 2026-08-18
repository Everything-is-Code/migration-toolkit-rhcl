package com.redhat.migrationtoolkit.rhcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Locks semantic defaults for conversion placeholders, ports, and 3scale list page size.
 */
class ConversionConstantsTest {

    @Test
    void credentialPlaceholder_isReplaceMe() {
        assertEquals("REPLACE_ME", ConversionConstants.CREDENTIAL_PLACEHOLDER);
    }

    @Test
    void defaultOidcIssuer_isGenericPlaceholderUrl() {
        assertEquals(
                "https://your-oidc-provider/realms/your-realm",
                ConversionConstants.DEFAULT_OIDC_ISSUER_URL);
        assertFalse(ConversionConstants.DEFAULT_OIDC_ISSUER_URL.contains("amazonaws"));
    }

    @Test
    void defaultPorts_matchLegacySchemeDefaults() {
        assertEquals(8080, ConversionConstants.DEFAULT_INTERNAL_PORT);
        assertEquals(80, ConversionConstants.DEFAULT_HTTP_PORT);
        assertEquals(443, ConversionConstants.DEFAULT_HTTPS_PORT);
    }

    @Test
    void listPageSize_intAndAnnotationDefaultAlign() {
        assertEquals(500, ConversionConstants.LIST_PAGE_SIZE);
        assertEquals("500", ConversionConstants.LIST_PAGE_SIZE_DEFAULT);
        assertEquals(
                String.valueOf(ConversionConstants.LIST_PAGE_SIZE),
                ConversionConstants.LIST_PAGE_SIZE_DEFAULT);
    }
}
