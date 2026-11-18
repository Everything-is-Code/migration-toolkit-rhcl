package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Exposes the Istio gateway Service through an OpenShift edge Route.
 * TLS terminates at the platform edge; the Gateway listener stays on port 8080.
 */
@ApplicationScoped
@Priority(120)
public class OpenShiftRouteGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "route.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return !RegistryDiscoveryMarkers.isDiscoveryService(ctx);
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String host = ctx.publicHostname();
        StringBuilder yaml = new StringBuilder();
        yaml.append("apiVersion: route.openshift.io/v1\n");
        yaml.append("kind: Route\n");
        yaml.append("metadata:\n");
        yaml.append("  name: ").append(name).append('\n');
        yaml.append("  namespace: ").append(ctx.namespace).append('\n');
        yaml.append("spec:\n");
        if (host != null) {
            yaml.append("  host: ").append(host).append('\n');
        }
        yaml.append("  to:\n");
        yaml.append("    kind: Service\n");
        yaml.append("    name: ").append(name).append("-gateway-istio\n");
        yaml.append("    weight: 100\n");
        yaml.append("  port:\n");
        yaml.append("    targetPort: http\n");
        yaml.append("  tls:\n");
        yaml.append("    termination: edge\n");
        yaml.append("    insecureEdgeTerminationPolicy: Redirect\n");
        yaml.append("  wildcardPolicy: None\n");
        return yaml.toString();
    }
}
