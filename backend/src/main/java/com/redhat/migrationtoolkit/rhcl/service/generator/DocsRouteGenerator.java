package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PublicHost;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RegistryDiscoveryMarkers;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Serves {@code /q} without the product AuthPolicy so the developer portal can
 * fetch {@code /q/openapi}. The AuthPolicy targets {@code {name}-route} only.
 */
@ApplicationScoped
@Priority(450)
public class DocsRouteGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "docs-route.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return internalBackend(ctx) != null && !RegistryDiscoveryMarkers.isDiscoveryService(ctx);
    }

    @Override
    public String generate(ConversionContext ctx) {
        ResolvedBackend backend = internalBackend(ctx);
        if (backend == null) {
            return null;
        }
        String name = ctx.serviceKebabName;
        String host = ctx.publicHostname();
        String backendNamespace = PublicHost.namespaceOf(backend.privateEndpoint);
        StringBuilder yaml = new StringBuilder();
        yaml.append("apiVersion: gateway.networking.k8s.io/v1\n");
        yaml.append("kind: HTTPRoute\n");
        yaml.append("metadata:\n");
        yaml.append("  name: ").append(name).append("-docs-route\n");
        yaml.append("  namespace: ").append(ctx.namespace).append('\n');
        yaml.append("spec:\n");
        if (host != null) {
            yaml.append("  hostnames:\n");
            yaml.append("    - ").append(host).append('\n');
        }
        yaml.append("  parentRefs:\n");
        yaml.append("    - name: ").append(name).append("-gateway\n");
        yaml.append("      namespace: ").append(ctx.namespace).append('\n');
        yaml.append("      sectionName: http\n");
        yaml.append("  rules:\n");
        yaml.append("    - matches:\n");
        yaml.append("        - path:\n");
        yaml.append("            type: PathPrefix\n");
        yaml.append("            value: /q\n");
        yaml.append("      backendRefs:\n");
        yaml.append("        - name: ").append(backend.refName).append('\n');
        yaml.append("          port: ").append(backend.port).append('\n');
        if (backendNamespace != null && !backendNamespace.equals(ctx.namespace)) {
            yaml.append("          namespace: ").append(backendNamespace).append('\n');
        }
        return yaml.toString();
    }

    private static ResolvedBackend internalBackend(ConversionContext ctx) {
        if (ctx.resolvedBackends == null) {
            return null;
        }
        for (ResolvedBackend backend : ctx.resolvedBackends) {
            if (backend.type == BackendType.INTERNAL) {
                return backend;
            }
        }
        return null;
    }
}
