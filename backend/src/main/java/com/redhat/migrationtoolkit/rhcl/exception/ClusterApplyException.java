package com.redhat.migrationtoolkit.rhcl.exception;

import java.util.Map;

public class ClusterApplyException extends ApiException {

    public ClusterApplyException(String message) {
        super("APPLY_FAILED", 500, message);
    }

    public ClusterApplyException(String message, Throwable cause) {
        super("APPLY_FAILED", 500, message, cause);
    }

    public ClusterApplyException(String message, Throwable cause, Map<String, Object> details) {
        super("APPLY_FAILED", 500, message, details, cause);
    }

    public ClusterApplyException(String code, String message) {
        super(code, 500, message);
    }
}
