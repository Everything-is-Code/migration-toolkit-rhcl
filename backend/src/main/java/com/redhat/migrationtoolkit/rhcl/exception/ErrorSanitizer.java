package com.redhat.migrationtoolkit.rhcl.exception;

import java.util.regex.Pattern;

public final class ErrorSanitizer {
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "(access_token|token|api_key|apikey|password|secret)(\\s*[=:]\\s*)(\\S+)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern BEARER_PATTERN = Pattern.compile(
        "(authorization\\s*[:=]\\s*bearer|bearer)\\s+(\\S+)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern SHA256_PATTERN = Pattern.compile(
        "sha256~\\S+", Pattern.CASE_INSENSITIVE);

    private ErrorSanitizer() {}

    public static String sanitize(String message) {
        if (message == null) {
            return "An unexpected error occurred";
        }
        String result = TOKEN_PATTERN.matcher(message).replaceAll("$1=[REDACTED]");
        result = BEARER_PATTERN.matcher(result).replaceAll("$1 [REDACTED]");
        result = SHA256_PATTERN.matcher(result).replaceAll("[REDACTED]");
        return result;
    }
}
