package com.redhat.migrationtoolkit.rhcl.model;

public class CompatibilityItem {
    public String name;
    public String status; // SUPPORTED, WARNING, UNSUPPORTED
    public String message;
    /** Optional capability flag id (e.g. {@code corsNative}, {@code kuadrantPresent}). */
    public String capability;
    /** Optional human-readable version requirement when a capability is missing. */
    public String requiredVersion;

    public CompatibilityItem() {}

    public CompatibilityItem(String name, String status, String message) {
        this.name = name;
        this.status = status;
        this.message = message;
    }

    public CompatibilityItem(String name, String status, String message,
                             String capability, String requiredVersion) {
        this.name = name;
        this.status = status;
        this.message = message;
        this.capability = capability;
        this.requiredVersion = requiredVersion;
    }
}
