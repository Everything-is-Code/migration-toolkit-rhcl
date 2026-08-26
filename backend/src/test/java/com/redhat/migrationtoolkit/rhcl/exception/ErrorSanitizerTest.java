package com.redhat.migrationtoolkit.rhcl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorSanitizerTest {

    @Test
    void sanitize_accessTokenInMessage_redacted() {
        String result = ErrorSanitizer.sanitize("Failed with access_token=abc123 on endpoint");
        assertEquals("Failed with access_token=[REDACTED] on endpoint", result);
    }

    @Test
    void sanitize_bearerToken_redacted() {
        String result = ErrorSanitizer.sanitize("HTTP header: bearer xyz789token");
        assertFalse(result.contains("xyz789token"), "Bearer token value must not leak");
    }

    @Test
    void sanitize_nullMessage_returnsGenericMessage() {
        String result = ErrorSanitizer.sanitize(null);
        assertEquals("An unexpected error occurred", result);
    }

    @Test
    void sanitize_cleanMessage_unchanged() {
        String message = "No YAML files found in the uploaded ZIP archive";
        String result = ErrorSanitizer.sanitize(message);
        assertEquals(message, result);
    }

    @Test
    void sanitize_passwordField_redacted() {
        String result = ErrorSanitizer.sanitize("Connect failed: password=s3cr3t!");
        assertFalse(result.contains("s3cr3t"));
        assertEquals("Connect failed: password=[REDACTED]", result);
    }

    @Test
    void sanitize_apiKeyField_redacted() {
        String result = ErrorSanitizer.sanitize("api_key=my-api-key-value");
        assertEquals("api_key=[REDACTED]", result);
    }

    @Test
    void sanitize_caseInsensitive_redacted() {
        String result = ErrorSanitizer.sanitize("TOKEN=uppercaseToken");
        assertEquals("TOKEN=[REDACTED]", result);
    }

    @Test
    void sanitize_authorizationBearerHeader_bothPartsRedacted() {
        String result = ErrorSanitizer.sanitize("Header: Authorization: Bearer eyJhbGci.secret.value in request");
        assertFalse(result.contains("eyJhbGci"), "JWT must not leak");
        assertFalse(result.contains("secret"), "JWT must not leak");
    }

    @Test
    void sanitize_bearerSpaceToken_redacted() {
        String result = ErrorSanitizer.sanitize("bearer eyJhbGci.payload.sig");
        assertFalse(result.contains("eyJhbGci"), "Bearer token must not leak");
    }

    @Test
    void sanitize_sha256Token_redacted() {
        String result = ErrorSanitizer.sanitize("Failed with sha256~abcdef123456 on cluster");
        assertFalse(result.contains("abcdef123456"), "sha256~ token must not leak");
    }

    @Test
    void sanitize_jsonQuotedAccessToken_redacted() {
        String result = ErrorSanitizer.sanitize("Body: {\"access_token\":\"secret-json-token\"}");
        assertFalse(result.contains("secret-json-token"), "JSON token must not leak");
        assertEquals("Body: {\"access_token\":\"[REDACTED]\"}", result);
    }

    @Test
    void sanitize_authorizationBasic_redacted() {
        String result = ErrorSanitizer.sanitize("Authorization: Basic dXNlcjpwYXNz");
        assertFalse(result.contains("dXNlcjpwYXNz"), "Basic auth must not leak");
    }

    @Test
    void sanitize_authorizationToken_redacted() {
        String result = ErrorSanitizer.sanitize("Authorization: Token abc123secret");
        assertFalse(result.contains("abc123secret"), "Token auth must not leak");
    }

    @Test
    void sanitize_xApiKeyHeader_redacted() {
        String result = ErrorSanitizer.sanitize("X-Api-Key: my-secret-key");
        assertFalse(result.contains("my-secret-key"), "X-Api-Key must not leak");
    }

    @Test
    void sanitizeExceptionMessage_usesClassNameWhenMessageNull() {
        String result = ErrorSanitizer.sanitizeExceptionMessage(new RuntimeException());
        assertEquals("RuntimeException", result);
    }

    @Test
    void sanitizeExceptionMessage_nullException_returnsGenericMessage() {
        String result = ErrorSanitizer.sanitizeExceptionMessage(null);
        assertEquals("An unexpected error occurred", result);
    }

    @Test
    void sanitizeExceptionMessage_sanitizesSensitiveMessage() {
        String result = ErrorSanitizer.sanitizeExceptionMessage(
                new RuntimeException("Failed with access_token=leaked-value"));
        assertEquals("Failed with access_token=[REDACTED]", result);
    }

    @Test
    void sanitize_colonSeparator_redacted() {
        String result = ErrorSanitizer.sanitize("secret: hidden-value");
        assertEquals("secret=[REDACTED]", result);
    }

    @Test
    void sanitize_apikeyField_redacted() {
        String result = ErrorSanitizer.sanitize("apikey=inline-key");
        assertEquals("apikey=[REDACTED]", result);
    }
}
