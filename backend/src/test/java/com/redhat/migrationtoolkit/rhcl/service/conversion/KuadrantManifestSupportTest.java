package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.ManifestMeta;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KuadrantManifestSupportTest {

    @Test
    void baseLabels_includesMigratedFromWhenRequested() {
        Map<String, String> labels = KuadrantManifestSupport.baseLabels("my-api", true);
        assertEquals("my-api", labels.get("app"));
        assertEquals("3scale", labels.get("migrated-from"));
    }

    @Test
    void baseLabels_omitsMigratedFromWhenDisabled() {
        Map<String, String> labels = KuadrantManifestSupport.baseLabels("my-api", false);
        assertEquals("my-api", labels.get("app"));
        assertFalse(labels.containsKey("migrated-from"));
    }

    @Test
    void meta_buildsManifestMetaWithLabels() {
        ManifestMeta meta = KuadrantManifestSupport.meta("my-policy", "demo-ns", "my-api", true);
        assertEquals("my-policy", meta.name());
        assertEquals("demo-ns", meta.namespace());
        assertEquals("my-api", meta.labels().get("app"));
    }

    @Test
    void resolveSerializer_prefersInjectedInstance() {
        ManifestSerializer injected = new ManifestSerializer();
        assertSame(injected, KuadrantManifestSupport.resolveSerializer(injected));
    }
}
