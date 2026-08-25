package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Mutable accumulator for HTTPRoute YAML fragments assembled by contributors.
 */
public final class HttpRouteBuilder {

    private final String name;
    private final String namespace;
    private final List<ResolvedBackend> backends;
    private final StringBuilder annotationBody = new StringBuilder();
    private final StringBuilder rulesBody = new StringBuilder();
    private final LinkedHashSet<String> pathsForOptions = new LinkedHashSet<>();
    private final StringBuilder sharedFilters = new StringBuilder();
    private String timeoutsBlock = "";
    private String retryBlock = "";
    private boolean corsEnabled = false;
    private String discoveryMarker = null;

    public HttpRouteBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
        this.backends = ctx.resolvedBackends;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public List<ResolvedBackend> backends() {
        return backends;
    }

    public LinkedHashSet<String> pathsForOptions() {
        return pathsForOptions;
    }

    public String sharedFilters() {
        return sharedFilters.toString();
    }

    public void appendSharedFilters(String fragment) {
        sharedFilters.append(fragment);
    }

    public String timeoutsBlock() {
        return timeoutsBlock;
    }

    public void setTimeoutsBlock(String block) {
        timeoutsBlock = block != null ? block : "";
    }

    public String retryBlock() {
        return retryBlock;
    }

    public void setRetryBlock(String block) {
        retryBlock = block != null ? block : "";
    }

    public boolean corsEnabled() {
        return corsEnabled;
    }

    public void setCorsEnabled(boolean enabled) {
        corsEnabled = enabled;
    }

    public void appendAnnotationBody(String body) {
        if (body != null && !body.isBlank()) {
            annotationBody.append(body);
        }
    }

    public void appendRule(String ruleYaml) {
        rulesBody.append(ruleYaml);
    }

    public void addPathForOptions(String path) {
        pathsForOptions.add(path);
    }

    /** Test-only discovery marker appended to metadata annotations. */
    public void setDiscoveryMarker(String marker) {
        discoveryMarker = marker;
    }

    public String build() {
        String annotations = buildAnnotationsBlock();
        if (discoveryMarker != null) {
            if (annotationBody.length() == 0) {
                annotations = "  annotations:\n    " + discoveryMarker + "\n";
            } else {
                annotations = "  annotations:\n" + annotationBody + "    " + discoveryMarker + "\n";
            }
        }
        return """
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: %s-route
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
%sspec:
  parentRefs:
    - name: %s-gateway
      namespace: %s
      sectionName: http
  rules:
%s""".formatted(name, namespace, name, annotations, name, namespace, rulesBody);
    }

    private String buildAnnotationsBlock() {
        if (annotationBody.length() == 0) {
            return "";
        }
        return "  annotations:\n" + annotationBody;
    }
}
