package com.redhat.migrationtoolkit.rhcl.controller;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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

    @Inject
    KubernetesClient client;

    @GET
    @Path("/domain")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get cluster base domain from ingress.config.openshift.io/cluster")
    public Response getDomain() {
        try {
            ResourceDefinitionContext rdc = new ResourceDefinitionContext.Builder()
                    .withGroup("config.openshift.io")
                    .withVersion("v1")
                    .withKind("Ingress")
                    .withPlural("ingresses")
                    .withNamespaced(false)
                    .build();

            GenericKubernetesResource ingress = client.genericKubernetesResources(rdc)
                    .withName("cluster")
                    .get();

            if (ingress == null) {
                return Response.status(404).entity(Map.of("error", "Ingress config not found")).build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) ingress.getAdditionalProperties().get("spec");
            if (spec == null) {
                return Response.status(404).entity(Map.of("error", "spec not found in Ingress config")).build();
            }

            Object domain = spec.get("domain");
            if (domain == null || domain.toString().isBlank()) {
                return Response.status(404).entity(Map.of("error", "domain not set in Ingress config")).build();
            }

            return Response.ok(Map.of("domain", domain.toString())).build();

        } catch (Exception e) {
            LOG.warnf("Failed to get cluster domain: %s", e.getMessage());
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
