package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IstioManifestSupportTest {

    @Test
    void joinYamlChunks_insertsNewlineBeforeDocumentSeparator() {
        String first = """
                apiVersion: networking.istio.io/v1alpha3
                kind: ServiceEntry
                spec:
                  resolution: DNS""";
        String second = """
                apiVersion: v1
                kind: Service
                spec:
                  type: ExternalName""";

        String joined = IstioManifestSupport.joinYamlChunks(first, second);

        assertFalse(joined.contains("DNS---"), "separator must not glue to prior scalar");
        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(joined);
        assertEquals(2, docs.size());
        assertEquals("ServiceEntry", docs.get(0).get("kind"));
        assertEquals("Service", docs.get(1).get("kind"));
    }

    @Test
    void joinYamlChunks_skipsBlankChunks_andEndsWithNewline() {
        String chunk = "apiVersion: v1\nkind: ConfigMap\n";

        String joined = IstioManifestSupport.joinYamlChunks(null, "  ", chunk);

        assertEquals("apiVersion: v1\nkind: ConfigMap\n", joined);
        assertTrue(joined.endsWith("\n"));
    }

    @Test
    void joinYamlChunks_singleChunk_hasNoDocumentSeparator() {
        String chunk = "apiVersion: v1\nkind: Secret\n";

        String joined = IstioManifestSupport.joinYamlChunks(chunk);

        assertFalse(joined.contains("---"));
        assertEquals("Secret", YamlAssertions.parse(joined).get("kind"));
    }

    @Test
    void joinDocuments_serializesAndJoinsMultipleResources() {
        ConfigMap first = new ConfigMapBuilder()
                .withNewMetadata().withName("a").endMetadata()
                .build();
        ConfigMap second = new ConfigMapBuilder()
                .withNewMetadata().withName("b").endMetadata()
                .build();

        String joined = IstioManifestSupport.joinDocuments(new ManifestSerializer(), first, second);

        List<Map<String, Object>> docs = YamlAssertions.parseDocuments(joined);
        assertEquals(2, docs.size());
    }

    @Test
    void resolveSerializer_returnsInjectedOrDefault() {
        ManifestSerializer injected = new ManifestSerializer();

        assertSame(injected, IstioManifestSupport.resolveSerializer(injected));
        assertNotNull(IstioManifestSupport.resolveSerializer(null));
    }

    @Test
    void baseLabels_includesMigratedFromWhenRequested() {
        Map<String, String> withLabel = IstioManifestSupport.baseLabels("my-api", true);
        Map<String, String> withoutLabel = IstioManifestSupport.baseLabels("my-api", false);

        assertEquals("my-api", withLabel.get("app"));
        assertEquals("3scale", withLabel.get("migrated-from"));
        assertFalse(withoutLabel.containsKey("migrated-from"));
    }

    @Test
    void joinYamlChunks_allBlank_returnsEmptyString() {
        assertEquals("", IstioManifestSupport.joinYamlChunks(null, "", "   "));
    }

    @Test
    void loggingWorkloadLabels_selectsGatewayOrAppLabel() {
        assertEquals(
                "api-gateway",
                IstioManifestSupport.loggingWorkloadLabels("api", true)
                        .get("gateway.networking.k8s.io/gateway-name"));
        assertEquals("api", IstioManifestSupport.loggingWorkloadLabels("api", false).get("app"));
    }
}
