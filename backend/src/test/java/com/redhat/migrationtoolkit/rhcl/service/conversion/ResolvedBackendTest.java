package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedBackendTest {

    @Test
    void constructor_exposesAllFields() {
        ResolvedBackend backend = new ResolvedBackend(
                BackendType.EXTERNAL,
                "demo-api-backend",
                "demo-api-external",
                "demo-api-backend-tls",
                "api.example.com",
                443,
                true,
                "/v1",
                80,
                "https://api.example.com/v1");

        assertEquals(BackendType.EXTERNAL, backend.type);
        assertEquals("demo-api-backend", backend.refName);
        assertEquals("demo-api-external", backend.seName);
        assertEquals("demo-api-backend-tls", backend.drName);
        assertEquals("api.example.com", backend.externalHost);
        assertEquals(443, backend.port);
        assertTrue(backend.usesTls);
        assertEquals("/v1", backend.mountPath);
        assertEquals(80, backend.weight);
        assertEquals("https://api.example.com/v1", backend.privateEndpoint);
    }
}
