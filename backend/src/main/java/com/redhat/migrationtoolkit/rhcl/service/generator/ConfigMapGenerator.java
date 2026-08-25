package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(500)
public class ConfigMapGenerator implements ResourceGenerator {

    @Override
    public String outputKey() {
        return "configmap.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        var backends = ctx.resolvedBackends;
        String primary = "";
        if (backends != null && !backends.isEmpty()
                && backends.get(0).privateEndpoint != null
                && !backends.get(0).privateEndpoint.isBlank()) {
            primary = backends.get(0).privateEndpoint.trim();
        }
        String allUrls = backends == null ? "" : backends.stream()
                .map(b -> b.privateEndpoint)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        String overrideNote = ctx.overrideIgnored ? "ignored-multi-backend" : "not-applicable";
        return """
apiVersion: v1
kind: ConfigMap
metadata:
  name: %s-config
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
data:
  backend-url: "%s"
  backend-urls: "%s"
  external-backend-url-override: "%s"
  service-name: "%s"
  original-3scale-service-id: "%s"
""".formatted(name, namespace, name, primary, allUrls, overrideNote,
                ctx.service.name, ctx.service.id);
    }
}
