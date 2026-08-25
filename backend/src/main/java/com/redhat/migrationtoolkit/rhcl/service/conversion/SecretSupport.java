package com.redhat.migrationtoolkit.rhcl.service.conversion;

import java.security.SecureRandom;

/** Shared Secret generation helpers. */
public final class SecretSupport {

    private SecretSupport() {
    }

    public static String generateRandomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
