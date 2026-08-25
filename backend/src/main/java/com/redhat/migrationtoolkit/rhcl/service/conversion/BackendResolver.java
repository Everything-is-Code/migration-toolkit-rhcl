package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.util.StringUtils;
import com.redhat.migrationtoolkit.rhcl.util.UrlUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves 3scale backends into {@link ResolvedBackend} targets for HTTPRoute and Istio resources.
 */
@ApplicationScoped
public class BackendResolver {

    private static final Logger LOG = Logger.getLogger(BackendResolver.class);

    public List<ResolvedBackend> resolveBackends(ApiService service, String productName,
            String backendUrl, boolean overrideIgnored) {
        List<Backend> backends = service.backends != null ? service.backends : List.of();
        boolean applyOverride = backendUrl != null && !backendUrl.isBlank() && !overrideIgnored;

        if (applyOverride) {
            return List.of(resolveOne(productName, backendUrl.trim(), null, "/", null, false));
        }
        if (backends.isEmpty()) {
            return List.of(resolveOne(productName, null, null, "/", null, false));
        }
        boolean multi = backends.size() > 1;
        List<ResolvedBackend> resolved = new ArrayList<>(backends.size());
        for (Backend backend : backends) {
            String sys = backend.systemName != null && !backend.systemName.isBlank()
                    ? StringUtils.toKebabCase(backend.systemName)
                    : (backend.name != null ? StringUtils.toKebabCase(backend.name) : "backend");
            resolved.add(resolveOne(
                    productName,
                    backend.privateEndpoint,
                    sys,
                    normalizeMountPath(backend.path),
                    backend.weight,
                    multi));
        }
        return disambiguateBackendNames(resolved);
    }

    List<ResolvedBackend> disambiguateBackendNames(List<ResolvedBackend> resolved) {
        if (resolved == null || resolved.size() < 2) {
            return resolved == null ? List.of() : resolved;
        }
        Set<String> usedRef = new HashSet<>();
        Set<String> usedSe = new HashSet<>();
        Set<String> usedDr = new HashSet<>();
        List<ResolvedBackend> out = new ArrayList<>(resolved.size());
        for (ResolvedBackend b : resolved) {
            String ref = uniqueResourceName(b.refName, usedRef);
            String se = uniqueResourceName(b.seName, usedSe);
            String dr = uniqueResourceName(b.drName, usedDr);
            if (!ref.equals(b.refName) || !se.equals(b.seName) || !dr.equals(b.drName)) {
                LOG.warnf(
                        "Disambiguated colliding multi-backend resource names: ref/se/dr %s/%s/%s → %s/%s/%s",
                        b.refName, b.seName, b.drName, ref, se, dr);
                out.add(new ResolvedBackend(
                        b.type, ref, se, dr, b.externalHost, b.port, b.usesTls,
                        b.mountPath, b.weight, b.privateEndpoint));
            } else {
                out.add(b);
            }
        }
        return out;
    }

    static String uniqueResourceName(String base, Set<String> used) {
        String candidate = base != null && !base.isBlank() ? base : "backend";
        if (used.add(candidate)) {
            return candidate;
        }
        int n = 2;
        while (!used.add(candidate + "-" + n)) {
            n++;
        }
        return candidate + "-" + n;
    }

    ResolvedBackend resolveOne(String productName, String url, String backendSys,
            String mountPath, Integer weight, boolean multi) {
        BackendType type = detectBackendType(url);
        String externalHost = type == BackendType.EXTERNAL ? UrlUtils.extractHostname(url) : null;
        String internalService = type == BackendType.INTERNAL
                ? UrlUtils.extractInternalService(url, productName) : null;
        int defaultPort = type == BackendType.EXTERNAL
                ? (url != null && url.trim().startsWith("http://")
                        ? ConversionConstants.DEFAULT_HTTP_PORT
                        : ConversionConstants.DEFAULT_HTTPS_PORT)
                : ConversionConstants.DEFAULT_INTERNAL_PORT;
        int port = UrlUtils.extractPort(url, defaultPort);
        boolean usesTls = type == BackendType.EXTERNAL
                && port == ConversionConstants.DEFAULT_HTTPS_PORT;

        String refName;
        String seName;
        String drName;
        if (multi && backendSys != null) {
            refName = productName + "-" + backendSys + "-backend";
            seName = productName + "-" + backendSys + "-external";
            drName = productName + "-" + backendSys + "-backend-tls";
        } else if (type == BackendType.INTERNAL && internalService != null) {
            refName = internalService;
            seName = productName + "-external";
            drName = productName + "-backend-tls";
        } else {
            refName = productName + "-backend";
            seName = productName + "-external";
            drName = productName + "-backend-tls";
        }
        return new ResolvedBackend(type, refName, seName, drName, externalHost, port, usesTls,
                mountPath, weight, url);
    }

    public static String normalizeMountPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.trim();
    }

    public BackendType detectBackendType(String url) {
        if (url == null || url.isBlank()) {
            return BackendType.INTERNAL;
        }
        String host = UrlUtils.extractHostname(url);
        if (host == null) {
            return BackendType.INTERNAL;
        }
        if (host.endsWith(".svc") || host.endsWith(".svc.cluster.local")) {
            return BackendType.INTERNAL;
        }
        if (!host.contains(".")) {
            return BackendType.INTERNAL;
        }
        return BackendType.EXTERNAL;
    }
}
