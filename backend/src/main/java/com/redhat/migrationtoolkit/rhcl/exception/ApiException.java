package com.redhat.migrationtoolkit.rhcl.exception;

import java.util.Collections;
import java.util.Map;

public abstract class ApiException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, Object> details;

    protected ApiException(String code, int status, String message) {
        this(code, status, message, Collections.emptyMap(), null);
    }

    protected ApiException(String code, int status, String message, Throwable cause) {
        this(code, status, message, Collections.emptyMap(), cause);
    }

    protected ApiException(String code, int status, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
