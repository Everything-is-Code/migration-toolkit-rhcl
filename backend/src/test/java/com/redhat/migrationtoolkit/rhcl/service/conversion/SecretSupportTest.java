package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretSupportTest {

    @Test
    void generateRandomHex_lengthMatchesByteCount() {
        String hex8 = SecretSupport.generateRandomHex(8);
        String hex16 = SecretSupport.generateRandomHex(16);

        assertEquals(16, hex8.length());
        assertEquals(32, hex16.length());
        assertTrue(hex8.matches("[0-9a-f]+"));
    }
}
