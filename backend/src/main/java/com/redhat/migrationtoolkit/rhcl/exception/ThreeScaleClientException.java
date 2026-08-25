package com.redhat.migrationtoolkit.rhcl.exception;

public class ThreeScaleClientException extends ApiException {

    public ThreeScaleClientException(String message) {
        super("THREESCALE_CLIENT_ERROR", 502, message);
    }

    public ThreeScaleClientException(String message, Throwable cause) {
        super("THREESCALE_CLIENT_ERROR", 502, message, cause);
    }
}
