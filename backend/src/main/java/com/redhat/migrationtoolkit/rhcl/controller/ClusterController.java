package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import com.redhat.migrationtoolkit.rhcl.exception.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/cluster")
@ApplicationScoped
@Tag(name = "Cluster", description = "OpenShift cluster information")
public class ClusterController {

    private static final Logger LOG = Logger.getLogger(ClusterController.class);

    /** Backend Route name (must match deploy/backend/05-route.yaml) */
    static final String BACKEND_ROUTE_NAME = "migration-tool-backend";

    /**
     * Preferred namespaces for the backend Route (install.sh default + common locals).
     * Package-visible for unit tests.
     */
    static final List<String> DOMAIN_ROUTE_NAMESPACES = List.of(
            "migration-toolkit",
            "default"
    );

    /** Short TTL for successful domain resolution. Package-visible for tests. */
    static final long DOMAIN_CACHE_TTL_MS = 60_000L;

    @Inject
    KubernetesClient client;

    @Inject
    ClusterVersionService clusterVersionService;

    /**
     * Static so TTL survives CDI client proxies / resource re-instantiation in tests
     * (mirrors the short-TTL pattern used by {@link ClusterVersionService}).
     */
    private static volatile Map<String, String> domainCache;
    private static volatile long domainCacheAt;

    /** Overridable clock for TTL unit tests. */
    protected long nowMs() {
        return System.currentTimeMillis();
    }

    /** Package-visible for unit tests. */
    void clearDomainCache() {
        domainCache = null;
        domainCacheAt = 0L;
    }

    /**
     * Obtains the cluster domain from the hostname of the backend's own OpenShift Route.
     * The Route host follows the format "{name}-{namespace}.apps.{cluster-domain}",
     * so everything after "apps." is returned as the domain.
     *
     * Prefers namespaced {@code withName} probes on an allow-list, caches successes briefly,
     * and only falls back to a cluster-wide list when probes miss.
     */
    @GET
    @Path("/domain")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get cluster base domain from backend Route host")
    public Response getDomain() {
        try {
            Map<String, String> cached = domainCache;
            if (cached != null && (nowMs() - domainCacheAt) < DOMAIN_CACHE_TTL_MS) {
                return Response.ok(cached).build();
            }

            ResourceDefinitionContext rdc = new ResourceDefinitionContext.Builder()
                    .withGroup("route.openshift.io")
                    .withVersion("v1")
                    .withKind("Route")
                    .withPlural("routes")
                    .withNamespaced(true)
                    .build();

            GenericKubernetesResource backendRoute = findBackendRoute(rdc);

            if (backendRoute == null) {
                LOG.warnf("Route '%s' not found", BACKEND_ROUTE_NAME);
                throw new NotFoundException("CLUSTER_ROUTE_NOT_FOUND",
                        "Route '" + BACKEND_ROUTE_NAME + "' not found");
            }

            String routeNamespace = backendRoute.getMetadata().getNamespace();
            String routeHost = extractRouteHost(backendRoute);

            if (routeHost == null || routeHost.isBlank()) {
                LOG.warnf("Route '%s' has no host assigned yet", BACKEND_ROUTE_NAME);
                throw new NotFoundException("CLUSTER_ROUTE_HOST_PENDING",
                        "Route host not assigned yet — wait for Route to be ready");
            }

            LOG.debugf("Backend route host: %s (namespace: %s)", routeHost, routeNamespace);

            int appsIdx = routeHost.indexOf(".apps.");
            if (appsIdx < 0) {
                LOG.warnf("Cannot extract cluster domain from route host: %s", routeHost);
                throw new NotFoundException("CLUSTER_DOMAIN_EXTRACT_FAILED",
                        "Cannot extract cluster domain from route host: " + routeHost);
            }

            String domain = routeHost.substring(appsIdx + 1);
            LOG.infof("Cluster domain: %s, namespace: %s", domain, routeNamespace);

            var result = new HashMap<String, String>();
            result.put("domain", domain);
            if (routeNamespace != null && !routeNamespace.isBlank()) {
                result.put("namespace", routeNamespace);
            }
            domainCache = Map.copyOf(result);
            domainCacheAt = nowMs();
            return Response.ok(result).build();

        } catch (com.redhat.migrationtoolkit.rhcl.exception.ApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf("Failed to get cluster domain: %s", e.getMessage());
            throw new RuntimeException("Failed to get cluster domain: " + e.getMessage(), e);
        }
    }

    /**
     * Probe allow-listed namespaces with withName first; fall back to cluster-wide list only on miss.
     * Package-visible counter for tests: increments when fallback list is used.
     */
    volatile int clusterWideListInvocations;

    private GenericKubernetesResource findBackendRoute(ResourceDefinitionContext rdc) {
        for (String ns : DOMAIN_ROUTE_NAMESPACES) {
            try {
                GenericKubernetesResource route = client.genericKubernetesResources(rdc)
                        .inNamespace(ns)
                        .withName(BACKEND_ROUTE_NAME)
                        .get();
                if (route != null) {
                    LOG.debugf("Found backend Route in allow-list namespace %s", ns);
                    return route;
                }
            } catch (Exception e) {
                LOG.debugf("Route probe in namespace %s failed: %s", ns, e.getMessage());
            }
        }

        clusterWideListInvocations++;
        LOG.debugf("Allow-list miss for '%s'; falling back to cluster-wide Route list", BACKEND_ROUTE_NAME);
        var allRoutes = client.genericKubernetesResources(rdc).inAnyNamespace().list();
        LOG.debugf("Found %d routes in cluster", allRoutes.getItems().size());
        return allRoutes.getItems().stream()
                .filter(r -> BACKEND_ROUTE_NAME.equals(r.getMetadata().getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolved cluster versions and capability matrix.
     * Profile override is read from settings key {@code clusterProfile}
     * ({@code auto}|{@code ocp-4.19}|{@code ocp-4.21}).
     */
    @GET
    @Path("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get detected or profile/default cluster versions and capabilities")
    public Response getVersions(@QueryParam("refresh") @DefaultValue("false") boolean refresh) {
        ClusterVersionsResponse versions = clusterVersionService.resolveFromSettings(refresh);
        return Response.ok(versions).build();
    }

    @SuppressWarnings("unchecked")
    private String extractRouteHost(GenericKubernetesResource route) {
        try {
            Map<String, Object> spec = (Map<String, Object>) route.getAdditionalProperties().get("spec");
            if (spec != null) {
                Object host = spec.get("host");
                if (host != null) {
                    return host.toString();
                }
            }
            Map<String, Object> status = (Map<String, Object>) route.getAdditionalProperties().get("status");
            if (status != null) {
                var ingresses = (java.util.List<Map<String, Object>>) status.get("ingress");
                if (ingresses != null && !ingresses.isEmpty()) {
                    Object host = ingresses.get(0).get("host");
                    if (host != null) {
                        return host.toString();
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error extracting host from route: %s", e.getMessage());
        }
        return null;
    }
}
