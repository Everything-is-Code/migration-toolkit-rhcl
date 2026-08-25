package com.redhat.migrationtoolkit.rhcl.exception;

public class ImportParseException extends ApiException {

    public ImportParseException(String message) {
        super("IMPORT_PARSE_ERROR", 400, message);
    }

    public ImportParseException(String message, Throwable cause) {
        super("IMPORT_PARSE_ERROR", 400, message, cause);
    }

    public static ImportParseException noYaml() {
        return new ImportParseException("IMPORT_NO_YAML", "No YAML files found in ZIP");
    }

    private ImportParseException(String code, String message) {
        super(code, 400, message);
    }
}
