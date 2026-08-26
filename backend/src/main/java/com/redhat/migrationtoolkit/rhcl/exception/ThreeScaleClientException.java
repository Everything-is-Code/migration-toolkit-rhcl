package com.redhat.migrationtoolkit.rhcl.exception;

public class ThreeScaleClientException extends ApiException {

    public ThreeScaleClientException(String message) {
        super("THREESCALE_CLIENT_ERROR", 502, message);
    }

    public ThreeScaleClientException(String message, Throwable cause) {
        super("THREESCALE_CLIENT_ERROR", 502, message, cause);
    }

    public ThreeScaleClientException(String code, String message) {
        super(code, 502, message);
    }
}
