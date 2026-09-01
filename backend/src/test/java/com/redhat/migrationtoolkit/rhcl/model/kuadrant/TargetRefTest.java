package com.redhat.migrationtoolkit.rhcl.model.kuadrant;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetRefTest {

    private final ManifestSerializer serializer = new ManifestSerializer();

    @Test
    void serialization_matchesGatewayTargetShape() {
        TargetRef ref = new TargetRef("gateway.networking.k8s.io", "Gateway", "demo-api-gateway");

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(ref));

        assertEquals("gateway.networking.k8s.io", parsed.get("group"));
        assertEquals("Gateway", parsed.get("kind"));
        assertEquals("demo-api-gateway", parsed.get("name"));
    }

    @Test
    void blankName_serializesAsEmptyString() {
        TargetRef ref = new TargetRef("gateway.networking.k8s.io", "Gateway", "");

        Map<String, Object> parsed = YamlAssertions.parse(serializer.toYaml(ref));

        assertEquals("", parsed.get("name"));
    }
}
