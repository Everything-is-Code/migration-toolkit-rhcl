package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterCapabilities;
import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionRequest;
import com.redhat.migrationtoolkit.rhcl.entity.ConversionHistoryEntity;
import com.redhat.migrationtoolkit.rhcl.entity.ProjectEntity;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.CompatibilityResult;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import com.redhat.migrationtoolkit.rhcl.service.CompatibilityService;
import com.redhat.migrationtoolkit.rhcl.service.ConversionService;
import com.redhat.migrationtoolkit.rhcl.service.ThreeScaleExportService;
import com.redhat.migrationtoolkit.rhcl.service.ValidationService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Path("/api/convert")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Conversion", description = "Convert 3scale services to Connectivity Link YAML")
public class ConversionController {

    private static final Logger LOG = Logger.getLogger(ConversionController.class);

    /** Max wait for virtual-thread export prefetch pool shutdown. */
    private static final long PREFETCH_TERMINATION_SECONDS = 120;

    @Inject
    ThreeScaleExportService exportService;

    @Inject
    CompatibilityService compatibilityService;

    @Inject
    ConversionService conversionService;

    @Inject
    ValidationService validationService;

    @Inject
    ClusterVersionService clusterVersionService;

    @POST
    @Transactional
    @Operation(summary = "Convert selected 3scale services")
    public Response convert(@Valid ConversionRequest request) {
        String namespace = request.namespace != null ? request.namespace : "default";

        if (Boolean.TRUE.equals(request.includeDnsPolicy)
                && (request.dnsHostname == null || request.dnsHostname.isBlank())) {
            return Response.status(400).entity(Map.of(
                    "error", "dnsHostname is required when includeDnsPolicy is true"
            )).build();
        }

        ProjectEntity project = new ProjectEntity();
        project.name = "Migration-" + System.currentTimeMillis();
        project.threescaleUrl = request.threescaleUrl;
        project.tenant = request.tenant;
        project.persist();

        ClusterCapabilities caps = resolveCapabilities();

        Map<String, PrefetchedExport> exports = prefetchExports(
                request.threescaleUrl, request.accessToken, request.serviceIds);

        List<Map<String, Object>> results = new ArrayList<>();

        for (String serviceId : request.serviceIds) {
            try {
                ApiService service = requirePrefetchedService(exports.get(serviceId), serviceId);
                Set<String> supportedPolicies = (request.supportedPolicies != null)
                        ? new HashSet<>(request.supportedPolicies)
                        : Set.of();
                CompatibilityResult compatibility = compatibilityService.check(
                        service, supportedPolicies, caps);
                ConversionOptions opts = new ConversionOptions();
                opts.loggingTarget = "workload".equals(request.loggingTarget) ? "workload" : "gateway";
                opts.anonymousTarget = "gateway".equals(request.anonymousTarget) ? "gateway" : "httproute";
                opts.includeMigratedFromLabel = !Boolean.FALSE.equals(request.includeMigratedFromLabel);
                opts.ipCheckMode = "authPolicyOpa".equals(request.ipCheckMode)
                        ? "authPolicyOpa" : "authorizationPolicy";
                opts.corsNative = caps != null && caps.corsNative;
                opts.retriesSupported = caps != null && caps.retriesSupported;
                opts.includeTlsPolicy = Boolean.TRUE.equals(request.includeTlsPolicy);
                opts.tlsIssuerKind = request.tlsIssuerKind;
                opts.tlsIssuerName = request.tlsIssuerName;
                opts.includeDnsPolicy = Boolean.TRUE.equals(request.includeDnsPolicy);
                opts.dnsHostname = request.dnsHostname;
                opts.dnsProviderSecretName = request.dnsProviderSecretName;
                Map<String, String> yamlFiles = conversionService.convert(
                        service, namespace, request.externalBackendUrl, opts);

                String name = service.systemName != null ? service.systemName : service.name;
                name = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");

                String yamlContent = String.join("\n---\n", yamlFiles.values());
                ConversionHistoryEntity history = new ConversionHistoryEntity();
                history.project = project;
                history.serviceId = serviceId;
                history.serviceName = service.name;
                history.status = "COMPLETED";
                history.compatibilityScore = compatibility.score;
                history.yamlContent = yamlContent;
                history.persist();

                Map<String, Object> result = new HashMap<>();
                result.put("serviceId", serviceId);
                result.put("serviceName", service.name);
                result.put("packageName", name);
                result.put("historyId", history.id);
                result.put("compatibilityScore", compatibility.score);
                result.put("files", new ArrayList<>(yamlFiles.keySet()));
                result.put("yamlFiles", yamlFiles);
                results.add(result);

            } catch (Exception e) {
                LOG.warnf(e, "Conversion FAILED for service %s: %s", serviceId, e.getMessage());
                ConversionHistoryEntity history = new ConversionHistoryEntity();
                history.project = project;
                history.serviceId = serviceId;
                history.status = "FAILED";
                history.persist();

                results.add(Map.of(
                        "serviceId", serviceId,
                        "status", "FAILED",
                        "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                ));
            }
        }

        return Response.ok(Map.of(
                "projectId", project.id,
                "results", results
        )).build();
    }

    /**
     * Pre-fetch all service exports concurrently (virtual threads). Convert and history
     * persistence stay sequential in {@link #convert} for transactional safety.
     */
    private Map<String, PrefetchedExport> prefetchExports(
            String threescaleUrl, String accessToken, List<String> serviceIds) {
        Map<String, PrefetchedExport> exports = new ConcurrentHashMap<>();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return exports;
        }
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(serviceIds.size());
            for (String serviceId : serviceIds) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        ApiService service = exportService.exportService(
                                threescaleUrl, accessToken, serviceId);
                        exports.put(serviceId, PrefetchedExport.ok(service));
                    } catch (Exception e) {
                        exports.put(serviceId, PrefetchedExport.failed(e));
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(PREFETCH_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return exports;
    }

    private static ApiService requirePrefetchedService(PrefetchedExport prefetched, String serviceId) {
        if (prefetched == null) {
            throw new IllegalStateException("Missing prefetched export for service " + serviceId);
        }
        if (prefetched.error() != null) {
            throw wrap(prefetched.error());
        }
        return prefetched.service();
    }

    private static RuntimeException wrap(Exception error) {
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new RuntimeException(error);
    }

    private ClusterCapabilities resolveCapabilities() {
        ClusterVersionsResponse versions = clusterVersionService.resolveFromSettings(false);
        return versions != null ? versions.capabilities : null;
    }

    private record PrefetchedExport(ApiService service, Exception error) {
        static PrefetchedExport ok(ApiService service) {
            return new PrefetchedExport(service, null);
        }

        static PrefetchedExport failed(Exception error) {
            return new PrefetchedExport(null, error);
        }
    }
}
