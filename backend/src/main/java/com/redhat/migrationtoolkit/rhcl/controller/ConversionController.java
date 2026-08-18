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

@Path("/api/convert")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Conversion", description = "Convert 3scale services to Connectivity Link YAML")
public class ConversionController {

    private static final Logger LOG = Logger.getLogger(ConversionController.class);

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

        ProjectEntity project = new ProjectEntity();
        project.name = "Migration-" + System.currentTimeMillis();
        project.threescaleUrl = request.threescaleUrl;
        project.tenant = request.tenant;
        project.persist();

        ClusterCapabilities caps = resolveCapabilities();

        List<Map<String, Object>> results = new ArrayList<>();

        for (String serviceId : request.serviceIds) {
            try {
                ApiService service = exportService.exportService(
                        request.threescaleUrl, request.accessToken, serviceId);
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

    private ClusterCapabilities resolveCapabilities() {
        ClusterVersionsResponse versions = clusterVersionService.resolveFromSettings(false);
        return versions != null ? versions.capabilities : null;
    }
}
