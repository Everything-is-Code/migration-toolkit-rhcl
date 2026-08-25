package com.redhat.migrationtoolkit.rhcl.service.conversion;

/**
 * Resolved conversion target for one product backend (or a synthetic override/default).
 */
public final class ResolvedBackend {

    public final BackendType type;
    public final String refName;
    public final String seName;
    public final String drName;
    public final String externalHost;
    public final int port;
    public final boolean usesTls;
    public final String mountPath;
    public final Integer weight;
    public final String privateEndpoint;

    public ResolvedBackend(BackendType type, String refName, String seName, String drName,
            String externalHost, int port, boolean usesTls,
            String mountPath, Integer weight, String privateEndpoint) {
        this.type = type;
        this.refName = refName;
        this.seName = seName;
        this.drName = drName;
        this.externalHost = externalHost;
        this.port = port;
        this.usesTls = usesTls;
        this.mountPath = mountPath;
        this.weight = weight;
        this.privateEndpoint = privateEndpoint;
    }
}
