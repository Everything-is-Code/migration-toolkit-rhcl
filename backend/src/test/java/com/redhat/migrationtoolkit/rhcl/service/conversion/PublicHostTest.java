package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicHostTest {

    @Test
    void fromIssuer_appsHost_usesProductAndNamespace() {
        String host = PublicHost.fromIssuer(
                "https://rhbk.apps.ocp.example.com/realms/neuralbank",
                "neuralbank-oidc",
                "business-apis");
        assertEquals("neuralbank-oidc-business-apis.apps.ocp.example.com", host);
    }

    @Test
    void fromIssuer_withoutAppsDomain_returnsNull() {
        assertNull(PublicHost.fromIssuer(
                "https://your-oidc-provider/realms/your-realm",
                "demo",
                "default"));
    }

    @Test
    void namespaceOf_clusterServiceUrl() {
        assertEquals("neuralbank-stack", PublicHost.namespaceOf(
                "http://neuralbank-backend-svc.neuralbank-stack.svc.cluster.local:8080"));
        assertNull(PublicHost.namespaceOf("http://neuralbank-backend-svc:8080"));
    }

    @Test
    void stripUsagePrefix_removesBackendMount() {
        assertEquals("/api/v1/customers", PublicHost.stripUsagePrefix("/customers/api/v1/customers", "/customers"));
        assertEquals("/api/v1/loans", PublicHost.stripUsagePrefix("/api/v1/loans", "/"));
        assertEquals("/", PublicHost.stripUsagePrefix("/usage", "/usage"));
    }
}
