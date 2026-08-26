package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthPolicySupportTest {

    @Test
    void firstNonBlank_skipsNullBlankAndLiteralNull() {
        assertNull(AuthPolicySupport.firstNonBlank());
        assertNull(AuthPolicySupport.firstNonBlank(null, "  ", "null", ""));
        assertEquals("value", AuthPolicySupport.firstNonBlank(null, "null", "  value  "));
    }
}
