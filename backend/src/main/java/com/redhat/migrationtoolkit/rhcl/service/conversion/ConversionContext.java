package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.util.StringUtils;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Per-invocation inputs and derived state for one {@code convert()} call.
 */
public final class ConversionContext {

    private static final Logger LOG = Logger.getLogger(ConversionContext.class);

    public final ApiService service;
    public final String namespace;
    public final String backendUrl;
    public final ConversionOptions options;
    public final String serviceKebabName;
    public final String loggingTarget;
    public final String anonymousTarget;
    public final boolean includeMigratedFromLabel;
    public final String ipCheckMode;
    public final boolean overrideIgnored;
    public final List<ResolvedBackend> resolvedBackends;
    public final BackendType primaryBackendType;
    public final String primaryExternalHost;

    private ConversionContext(ApiService service, String namespace, String backendUrl,
            ConversionOptions options, String serviceKebabName, String loggingTarget,
            String anonymousTarget, boolean includeMigratedFromLabel, String ipCheckMode,
            boolean overrideIgnored, List<ResolvedBackend> resolvedBackends,
            BackendType primaryBackendType, String primaryExternalHost) {
        this.service = service;
        this.namespace = namespace;
        this.backendUrl = backendUrl;
        this.options = options;
        this.serviceKebabName = serviceKebabName;
        this.loggingTarget = loggingTarget;
        this.anonymousTarget = anonymousTarget;
        this.includeMigratedFromLabel = includeMigratedFromLabel;
        this.ipCheckMode = ipCheckMode;
        this.overrideIgnored = overrideIgnored;
        this.resolvedBackends = resolvedBackends;
        this.primaryBackendType = primaryBackendType;
        this.primaryExternalHost = primaryExternalHost;
    }

    public static ConversionContext build(ApiService service, String namespace, String backendUrl,
            ConversionOptions opts, BackendResolver backendResolver) {
        ConversionOptions options = opts != null ? opts : new ConversionOptions();
        String loggingTarget = options.loggingTarget != null ? options.loggingTarget : "gateway";
        String anonymousTarget = options.anonymousTarget != null ? options.anonymousTarget : "httproute";
        boolean includeMigratedFromLabel = options.includeMigratedFromLabel;
        String ipCheckMode = "authPolicyOpa".equals(options.ipCheckMode)
                ? "authPolicyOpa" : "authorizationPolicy";

        String serviceKebabName = StringUtils.toKebabCase(
                service.systemName != null ? service.systemName : service.name);

        int backendCount = service.backends != null ? service.backends.size() : 0;
        boolean overrideIgnored = backendUrl != null && !backendUrl.isBlank() && backendCount > 1;
        if (overrideIgnored) {
            LOG.warnf(
                    "externalBackendUrl override ignored for service %s: %d backends present; keeping path-based multi-backend routing",
                    serviceKebabName, backendCount);
        }

        List<ResolvedBackend> resolved = backendResolver.resolveBackends(
                service, serviceKebabName, backendUrl, overrideIgnored);
        BackendType primaryType = resolved.stream().anyMatch(b -> b.type == BackendType.EXTERNAL)
                ? BackendType.EXTERNAL : BackendType.INTERNAL;
        String primaryExternalHost = resolved.stream()
                .filter(b -> b.type == BackendType.EXTERNAL && b.externalHost != null)
                .map(b -> b.externalHost)
                .findFirst()
                .orElse(null);

        return new ConversionContext(service, namespace, backendUrl, options, serviceKebabName,
                loggingTarget, anonymousTarget, includeMigratedFromLabel, ipCheckMode,
                overrideIgnored, resolved, primaryType, primaryExternalHost);
    }

    /** True when DNSPolicy opt-in is on and hostname is non-blank (avoids inert CRs). */
    public boolean emitDnsPolicy() {
        return emitDnsPolicy(options);
    }

    public static boolean emitDnsPolicy(ConversionOptions opts) {
        return opts != null
                && opts.includeDnsPolicy
                && opts.dnsHostname != null
                && !opts.dnsHostname.isBlank();
    }
}
