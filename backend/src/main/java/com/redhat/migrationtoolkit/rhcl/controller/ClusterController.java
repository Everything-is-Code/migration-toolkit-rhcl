package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.dto.ClusterVersionsResponse;
import com.redhat.migrationtoolkit.rhcl.entity.AppSettingsEntity;
import com.redhat.migrationtoolkit.rhcl.service.ClusterVersionService;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
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

import java.util.Map;

@Path("/api/cluster")
@Tag(name = "Cluster", description = "OpenShift cluster information")
public class ClusterController {

    private static final Logger LOG = Logger.getLogger(ClusterController.class);

    /** Backend Route name (must match deploy/backend/05-route.yaml) */
    private static final String BACKEND_ROUTE_NAME = "migration-tool-backend";

    @Inject
    KubernetesClient client;

    @Inject
    ClusterVersionService clusterVersionService;

    /**
     * Obtains the cluster domain from the hostname of the backend's own OpenShift Route.
     * The Route host follows the format "{name}-{namespace}.apps.{cluster-domain}",
     * so everything after "apps." is returned as the domain.
     *
     * This approach does not require config.openshift.io permissions and works
     * with the existing route.openshift.io permissions (06-rbac.yaml).
     */
    @GET
    @Path("/domain")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get cluster base domain from backend Route host")
    public Response getDomain() {
        try {
            ResourceDefinitionContext rdc = new ResourceDefinitionContext.Builder()
                    .withGroup("route.openshift.io")
                    .withVersion("v1")
                    .withKind("Route")
                    .withPlural("routes")
                    .withNamespaced(true)
                    .build();

            // Search across all namespaces to find the backend Route
            var allRoutes = client.genericKubernetesResources(rdc).inAnyNamespace().list();
            LOG.debugf("Found %d routes in cluster", allRoutes.getItems().size());

            GenericKubernetesResource backendRoute = allRoutes.getItems().stream()
                    .filter(r -> BACKEND_ROUTE_NAME.equals(r.getMetadata().getName()))
                    .findFirst()
                    .orElse(null);

            if (backendRoute == null) {
                LOG.warnf("Route '%s' not found", BACKEND_ROUTE_NAME);
                return Response.status(404).entity(Map.of(
                        "error", "Route '" + BACKEND_ROUTE_NAME + "' not found")).build();
            }

            String routeNamespace = backendRoute.getMetadata().getNamespace();
            String routeHost = extractRouteHost(backendRoute);

            if (routeHost == null || routeHost.isBlank()) {
                LOG.warnf("Route '%s' has no host assigned yet", BACKEND_ROUTE_NAME);
                return Response.status(404).entity(Map.of(
                        "error", "Route host not assigned yet — wait for Route to be ready")).build();
            }

            LOG.debugf("Backend route host: %s (namespace: %s)", routeHost, routeNamespace);

            // Extract the domain as everything after "apps." in the hostname
            // e.g.: migration-tool-backend-myns.apps.cluster-abc.example.com
            //  -> apps.cluster-abc.example.com
            int appsIdx = routeHost.indexOf(".apps.");
            if (appsIdx < 0) {
                LOG.warnf("Cannot extract cluster domain from route host: %s", routeHost);
                return Response.status(404).entity(Map.of(
                        "error", "Cannot extract cluster domain from route host: " + routeHost)).build();
            }

            String domain = routeHost.substring(appsIdx + 1); // "apps.cluster-abc.example.com"
            LOG.infof("Cluster domain: %s, namespace: %s", domain, routeNamespace);

            var result = new java.util.HashMap<String, String>();
            result.put("domain", domain);
            if (routeNamespace != null && !routeNamespace.isBlank()) {
                result.put("namespace", routeNamespace);
            }
            return Response.ok(result).build();

        } catch (Exception e) {
            LOG.warnf("Failed to get cluster domain: %s", e.getMessage());
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
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
        String profile = ClusterVersionService.PROFILE_AUTO;
        try {
            AppSettingsEntity entity = AppSettingsEntity.findById(
                    ClusterVersionService.SETTINGS_KEY_CLUSTER_PROFILE);
            if (entity != null && entity.value != null && !entity.value.isBlank()) {
                profile = entity.value.trim();
            }
        } catch (Exception e) {
            LOG.debugf("clusterProfile setting unavailable: %s", e.getMessage());
        }
        ClusterVersionsResponse versions = clusterVersionService.resolve(profile, refresh);
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
            // Also check status.ingress[0].host as a fallback
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
