package com.redhat.migrationtoolkit.rhcl.exception;

import java.util.Map;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super("VALIDATION_FAILED", 400, message);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super("VALIDATION_FAILED", 400, message, details, null);
    }
}
