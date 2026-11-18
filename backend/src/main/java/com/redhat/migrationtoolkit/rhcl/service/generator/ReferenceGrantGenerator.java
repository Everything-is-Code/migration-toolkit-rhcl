package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PublicHost;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows an HTTPRoute to select a Service that lives in the backend namespace.
 * This cluster serves ReferenceGrant only as gateway.networking.k8s.io/v1beta1.
 */
@ApplicationScoped
@Priority(90)
public class ReferenceGrantGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "referencegrant.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return !grants(ctx).isEmpty();
    }

    @Override
    public String generate(ConversionContext ctx) {
        StringBuilder yaml = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> grant : grants(ctx).entrySet()) {
            if (!first) {
                yaml.append("---\n");
            }
            first = false;
            yaml.append(document(ctx.serviceKebabName, ctx.namespace, grant.getKey(), grant.getValue()));
        }
        return yaml.toString();
    }

    static Map<String, List<String>> grants(ConversionContext ctx) {
        Map<String, List<String>> servicesByNamespace = new LinkedHashMap<>();
        if (ctx.resolvedBackends == null) {
            return servicesByNamespace;
        }
        for (ResolvedBackend backend : ctx.resolvedBackends) {
            if (backend.type != BackendType.INTERNAL) {
                continue;
            }
            String backendNamespace = PublicHost.namespaceOf(backend.privateEndpoint);
            if (backendNamespace == null || backendNamespace.equals(ctx.namespace)) {
                continue;
            }
            List<String> services = servicesByNamespace.computeIfAbsent(backendNamespace, key -> new ArrayList<>());
            if (!services.contains(backend.refName)) {
                services.add(backend.refName);
            }
        }
        return servicesByNamespace;
    }

    private static String document(String name, String fromNamespace, String backendNamespace, List<String> services) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("apiVersion: gateway.networking.k8s.io/v1beta1\n");
        yaml.append("kind: ReferenceGrant\n");
        yaml.append("metadata:\n");
        yaml.append("  name: ").append(name).append("-backend\n");
        yaml.append("  namespace: ").append(backendNamespace).append('\n');
        yaml.append("spec:\n");
        yaml.append("  from:\n");
        yaml.append("    - group: gateway.networking.k8s.io\n");
        yaml.append("      kind: HTTPRoute\n");
        yaml.append("      namespace: ").append(fromNamespace).append('\n');
        yaml.append("  to:\n");
        for (String service : services) {
            yaml.append("    - group: \"\"\n");
            yaml.append("      kind: Service\n");
            yaml.append("      name: ").append(service).append('\n');
        }
        return yaml.toString();
    }
}
