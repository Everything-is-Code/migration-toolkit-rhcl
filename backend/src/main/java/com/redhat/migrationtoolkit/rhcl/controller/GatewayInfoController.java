package com.redhat.migrationtoolkit.rhcl.controller;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.inject.Inject;
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
import com.redhat.migrationtoolkit.rhcl.exception.ValidationException;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

@Path("/api/gateway")
@Tag(name = "Gateway", description = "Gateway status and connection info")
public class GatewayInfoController {

    private static final Logger LOG = Logger.getLogger(GatewayInfoController.class);

    @Inject
    KubernetesClient client;

    /**
     * Returns the external access URL (LoadBalancer hostname / IP) of a Gateway.
     * Used by the frontend to generate test curl commands after apply.
     *
     * GET /api/gateway/info?namespace={ns}&name={gatewayName}
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get Gateway external URL from cluster")
    public Response getGatewayInfo(
            @QueryParam("namespace") String namespace,
            @QueryParam("name") String gatewayName) {

        if (namespace == null || namespace.isBlank() || gatewayName == null || gatewayName.isBlank()) {
            throw new ValidationException("namespace and name are required");
        }

        try {
            ResourceDefinitionContext rdc = new ResourceDefinitionContext.Builder()
                    .withGroup("gateway.networking.k8s.io")
                    .withVersion("v1")
                    .withKind("Gateway")
                    .withPlural("gateways")
                    .withNamespaced(true)
                    .build();

            GenericKubernetesResource gw = client.genericKubernetesResources(rdc)
                    .inNamespace(namespace)
                    .withName(gatewayName)
                    .get();

            if (gw == null) {
                throw new NotFoundException("GATEWAY_NOT_FOUND", "Gateway not found: " + gatewayName);
            }

            String hostname = extractHostname(gw);
            boolean lbReady  = hostname != null && !hostname.isBlank();
            boolean dnsReady = lbReady && isDnsResolvable(hostname);

            return Response.ok(Map.of(
                    "hostname", hostname != null ? hostname : "",
                    "httpUrl",  lbReady ? "http://"  + hostname : "",
                    "httpsUrl", lbReady ? "https://" + hostname : "",
                    "ready",    lbReady,
                    "dnsReady", dnsReady
            )).build();

        } catch (com.redhat.migrationtoolkit.rhcl.exception.ApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf("Failed to get Gateway info for %s/%s: %s", namespace, gatewayName, e.getMessage());
            throw new RuntimeException("Failed to get Gateway info: " + e.getMessage(), e);
        }
    }

    /** Check whether the ELB hostname is DNS-resolvable. */
    private boolean isDnsResolvable(String hostname) {
        try {
            InetAddress.getByName(hostname);
            return true;
        } catch (Exception e) {
            LOG.debugf("DNS not yet resolvable for hostname %s: %s", hostname, e.getMessage());
            return false;
        }
    }

    /** Retrieve Gateway status.addresses[0].value. */
    @SuppressWarnings("unchecked")
    private String extractHostname(GenericKubernetesResource gw) {
        try {
            Map<String, Object> status = (Map<String, Object>) gw.getAdditionalProperties().get("status");
            if (status == null) {
                return null;
            }
            List<Map<String, Object>> addresses = (List<Map<String, Object>>) status.get("addresses");
            if (addresses == null || addresses.isEmpty()) {
                return null;
            }
            Object value = addresses.get(0).get("value");
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            LOG.debugf("Failed to extract Gateway hostname from status: %s", e.getMessage());
            return null;
        }
    }
}
