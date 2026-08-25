package com.redhat.migrationtoolkit.rhcl.service.conversion;

/** Shared AuthPolicy YAML helpers (no ConversionService dependency). */
public final class AuthPolicySupport {

    private AuthPolicySupport() {
    }

    public static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                return s;
            }
        }
        return null;
    }
}
