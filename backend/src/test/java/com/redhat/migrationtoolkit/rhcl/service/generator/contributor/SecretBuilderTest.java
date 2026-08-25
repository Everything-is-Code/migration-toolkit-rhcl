package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretBuilderTest {

    @Test
    void build_assemblesSecretMetadata() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.setSecretYaml("""
apiVersion: v1
kind: Secret
metadata:
  name: demo-api-credentials
  namespace: ns
  labels:
    app: demo-api
    migrated-from: 3scale
type: Opaque
stringData:
  client-id: "REPLACE_ME"
  client-secret: "REPLACE_ME"
""");

        String yaml = builder.build();

        assertTrue(yaml.contains("name: demo-api-credentials"));
        assertTrue(yaml.contains("namespace: ns"));
        assertTrue(yaml.contains("client-id: \"REPLACE_ME\""));
    }

    @Test
    void build_insertsDiscoveryMarkerBeforeType() {
        ApiService service = new ApiService();
        service.name = "demo-api";
        service.systemName = "demo-api";

        ConversionContext ctx = ConversionContext.build(
                service, "ns", null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        builder.setSecretYaml("""
apiVersion: v1
kind: Secret
metadata:
  name: demo-api-credentials
  namespace: ns
type: Opaque
stringData:
  client-id: "REPLACE_ME"
""");
        builder.setDiscoveryMarker("x-discovery-marker: rhcl-secret-test");

        String yaml = builder.build();

        assertTrue(yaml.contains("x-discovery-marker: rhcl-secret-test"));
        assertTrue(yaml.indexOf("x-discovery-marker") < yaml.indexOf("type: Opaque"));
    }
}
