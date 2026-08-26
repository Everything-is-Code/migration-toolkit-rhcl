package com.redhat.migrationtoolkit.rhcl.exception;

public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(code, 404, message);
    }
}
