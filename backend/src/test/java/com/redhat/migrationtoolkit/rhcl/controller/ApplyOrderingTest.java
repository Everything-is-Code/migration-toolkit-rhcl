package com.redhat.migrationtoolkit.rhcl.controller;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyOrderingTest {

    @Test
    void targetNamespace_keepsDeclaredNamespace() {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace("neuralbank-stack");
        resource.setMetadata(meta);

        assertEquals("neuralbank-stack", ApplyController.targetNamespace(resource, "business-apis"));
        assertEquals("neuralbank-stack", resource.getMetadata().getNamespace());
    }

    @Test
    void targetNamespace_blankFallsBackToRequest() {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setMetadata(new ObjectMeta());

        assertEquals("business-apis", ApplyController.targetNamespace(resource, "business-apis"));
        assertEquals("business-apis", resource.getMetadata().getNamespace());
    }

    @Test
    void orderedFiles_referenceGrantBeforeRouteAndApiProductLast() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("apiproduct.yaml", "api");
        files.put("httproute.yaml", "http");
        files.put("policy.yaml", "policy");
        files.put("route.yaml", "route");
        files.put("gateway.yaml", "gw");
        files.put("referencegrant.yaml", "grant");
        files.put("docs-route.yaml", "docs");

        List<String> names = ApplyController.orderedFiles(files).stream().map(Map.Entry::getKey).toList();

        assertEquals(List.of(
                "referencegrant.yaml",
                "gateway.yaml",
                "route.yaml",
                "httproute.yaml",
                "docs-route.yaml",
                "policy.yaml",
                "apiproduct.yaml"), names);
    }

    @Test
    void normalizeApiVersion_keepsReferenceGrantOnV1beta1() {
        String yaml = """
                apiVersion: gateway.networking.k8s.io/v1beta1
                kind: ReferenceGrant
                """;
        String normalized = ApplyController.normalizeApiVersion(yaml);
        assertTrue(normalized.contains("gateway.networking.k8s.io/v1beta1"));
        assertFalse(normalized.contains("gateway.networking.k8s.io/v1\n"));
    }

    @Test
    void normalizeApiVersion_promotesGatewayToV1() {
        String yaml = """
                apiVersion: gateway.networking.k8s.io/v1beta1
                kind: Gateway
                """;
        String normalized = ApplyController.normalizeApiVersion(yaml);
        assertTrue(normalized.contains("apiVersion: gateway.networking.k8s.io/v1\n"));
        assertTrue(normalized.contains("kind: Gateway"));
    }

    @Test
    void openApiSpecUrl_readsQuotedAndPlainValues() {
        assertEquals("https://api.example.com/q/openapi?format=json",
                ApplyController.openApiSpecUrl("  openAPISpecURL: \"https://api.example.com/q/openapi?format=json\"\n"));
        assertEquals("https://api.example.com/q/openapi?format=json",
                ApplyController.openApiSpecUrl("openAPISpecURL: https://api.example.com/q/openapi?format=json\n"));
    }
}
