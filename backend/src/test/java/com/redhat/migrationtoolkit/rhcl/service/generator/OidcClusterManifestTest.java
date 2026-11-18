package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import com.redhat.migrationtoolkit.rhcl.model.Backend;
import com.redhat.migrationtoolkit.rhcl.model.MappingRule;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcClusterManifestTest {

    @Test
    void convert_oidcProduct_emitsRouteGrantDocsAndOpenApiUrl() {
        ApiService service = GeneratorTestSupport.basicService("NeuralBank OIDC", "neuralbank_oidc");
        Authentication auth = new Authentication();
        auth.type = "jwt";
        auth.oidcIssuerEndpoint = "https://rhbk.apps.ocp.example.com/realms/neuralbank";
        service.authentication = auth;

        Backend backend = new Backend();
        backend.systemName = "accounts";
        backend.privateEndpoint = "http://neuralbank-backend-svc.neuralbank-stack.svc.cluster.local:8080";
        backend.path = "/";
        service.backends = List.of(backend);

        MappingRule customers = new MappingRule();
        customers.httpMethod = "GET";
        customers.pattern = "/api/v1/customers";
        service.mappingRules = List.of(customers);

        ConversionContext ctx = GeneratorTestSupport.context(service, "business-apis",
                new com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions());
        Map<String, String> files = ResourceGeneratorRegistry.manual().generateAll(ctx);

        String host = "neuralbank-oidc-business-apis.apps.ocp.example.com";
        String route = files.get("route.yaml");
        assertTrue(route.contains("kind: Route"));
        assertTrue(route.contains("namespace: business-apis"));
        assertTrue(route.contains("host: " + host));
        assertTrue(route.contains("name: neuralbank-oidc-gateway-istio"));
        assertTrue(route.contains("termination: edge"));

        String grant = files.get("referencegrant.yaml");
        assertTrue(grant.contains("gateway.networking.k8s.io/v1beta1"));
        assertTrue(grant.contains("kind: ReferenceGrant"));
        assertTrue(grant.contains("namespace: neuralbank-stack"));
        assertTrue(grant.contains("namespace: business-apis"));
        assertTrue(grant.contains("name: neuralbank-backend-svc"));

        String http = files.get("httproute.yaml");
        assertTrue(http.contains("/api/v1/customers"));
        assertTrue(http.contains("namespace: \"neuralbank-stack\""));
        assertTrue(http.contains(host));

        String docs = files.get("docs-route.yaml");
        assertTrue(docs.contains("value: /q"));
        assertTrue(docs.contains("sectionName: http"));
        assertTrue(docs.contains("namespace: neuralbank-stack"));

        String product = files.get("apiproduct.yaml");
        assertTrue(product.contains("openAPISpecURL: https://" + host + "/q/openapi?format=json"));

        String gateway = files.get("gateway.yaml");
        assertTrue(gateway.contains("port: 8080"));
        assertTrue(gateway.contains(host));
    }
}
