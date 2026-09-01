package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRetry;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRule;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteTimeouts;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mutable accumulator for an HTTPRoute assembled by typed Fabric8 model objects.
 * Wraps a Fabric8 {@link io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder} internally.
 */
public final class HttpRouteBuilder {

    private static final Logger LOG = Logger.getLogger(HttpRouteBuilder.class);

    private final String name;
    private final String namespace;
    private final boolean includeMigratedFromLabel;
    private final List<ResolvedBackend> backends;
    private List<ResolvedBackend> overrideBackends;

    private final List<HTTPRouteRule> rules = new ArrayList<>();
    private final List<HTTPRouteFilter> sharedFilters = new ArrayList<>();
    private final LinkedHashSet<String> pathsForOptions = new LinkedHashSet<>();
    private HTTPRouteTimeouts timeouts;
    private HTTPRouteRetry retry;
    private boolean corsEnabled;
    private final Map<String, String> annotations = new LinkedHashMap<>();
    private String discoveryMarker;
    /** Raw YAML fragment for non-standard {@code type: CORS} filter (Istio/EnvoyProxy extension). */
    private String rawCorsFilterYaml;
    private final List<String> yamlCommentLines = new ArrayList<>();

    public HttpRouteBuilder(ConversionContext ctx) {
        this.name = ctx.serviceKebabName;
        this.namespace = ctx.namespace;
        this.includeMigratedFromLabel = ctx.includeMigratedFromLabel;
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

    /**
     * Optional upstream-policy override backends for mapping-rule fallthrough.
     * Not added to {@link ConversionContext#resolvedBackends}.
     */
    public void setOverrideBackends(List<ResolvedBackend> overrideBackends) {
        this.overrideBackends = overrideBackends;
    }

    /** Override backends when set; otherwise product {@link #backends()}. */
    public List<ResolvedBackend> effectiveBackends() {
        return overrideBackends != null ? overrideBackends : backends;
    }

    public void addRule(HTTPRouteRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    public void addAnnotation(String key, String value) {
        if (key != null && !key.isBlank()) {
            annotations.put(key, value != null ? value : "");
        }
    }

    public void addSharedFilter(HTTPRouteFilter filter) {
        if (filter != null) {
            sharedFilters.add(filter);
        }
    }

    public void setTimeouts(HTTPRouteTimeouts timeouts) {
        this.timeouts = timeouts;
    }

    public void setRetry(HTTPRouteRetry retry) {
        this.retry = retry;
    }

    public void setCorsEnabled(boolean enabled) {
        corsEnabled = enabled;
    }

    public void addPathForOptions(String path) {
        pathsForOptions.add(path);
    }

    /** Test-only discovery marker appended to metadata annotations. */
    public void setDiscoveryMarker(String marker) {
        discoveryMarker = marker;
    }

    public LinkedHashSet<String> pathsForOptions() {
        return pathsForOptions;
    }

    public List<HTTPRouteFilter> sharedFilters() {
        return sharedFilters;
    }

    public HTTPRouteTimeouts timeouts() {
        return timeouts;
    }

    public HTTPRouteRetry retry() {
        return retry;
    }

    public boolean corsEnabled() {
        return corsEnabled;
    }

    /** Store raw YAML fragment for the non-standard CORS filter extension. */
    public void setRawCorsFilterYaml(String yaml) {
        this.rawCorsFilterYaml = yaml != null && !yaml.isBlank() ? yaml : null;
    }

    /** Operator-visible YAML comment lines injected before {@code spec:} in serialized output. */
    public void addYamlComment(String comment) {
        if (comment != null && !comment.isBlank()) {
            yamlCommentLines.add(comment.trim());
        }
    }

    public String rawCorsFilterYaml() {
        return rawCorsFilterYaml;
    }

    public String build() {
        return build(resolveSerializer());
    }

    public String build(ManifestSerializer serializer) {
        Map<String, String> allAnnotations = new LinkedHashMap<>(annotations);
        if (discoveryMarker != null) {
            parseMarkerInto(allAnnotations, discoveryMarker.trim());
        }

        var routeBuilder = new io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder()
                .withApiVersion("gateway.networking.k8s.io/v1")
                .withKind("HTTPRoute");

        var meta = routeBuilder.withNewMetadata()
                .withName(name + "-route")
                .withNamespace(namespace);

        IstioManifestSupport.baseLabels(name, includeMigratedFromLabel)
                .forEach(meta::addToLabels);

        allAnnotations.forEach(meta::addToAnnotations);

        var specNested = meta.endMetadata()
                .withNewSpec()
                .addNewParentRef()
                .withName(name + "-gateway")
                .withNamespace(namespace)
                .withSectionName("http")
                .endParentRef();

        if (!rules.isEmpty()) {
            specNested.withRules(rules);
        }

        String yaml = serializer.toYaml(specNested.endSpec().build());
        if (!yamlCommentLines.isEmpty()) {
            yaml = injectYamlComments(yaml, yamlCommentLines);
        }
        if (rawCorsFilterYaml != null) {
            yaml = injectCorsFilters(yaml, rawCorsFilterYaml);
        }
        return yaml;
    }

    static String injectYamlComments(String yaml, List<String> comments) {
        if (comments == null || comments.isEmpty()) {
            return yaml;
        }
        String block = comments.stream()
                .map(line -> "  # " + line)
                .collect(Collectors.joining("\n")) + "\n";
        int specIdx = yaml.indexOf("\nspec:");
        if (specIdx < 0) {
            return yaml;
        }
        return yaml.substring(0, specIdx + 1) + block + yaml.substring(specIdx + 1);
    }

    /**
     * Inject raw CORS YAML into the filters section of each rule in the serialized output.
     * Used for the non-standard Istio {@code type: CORS} filter that has no Fabric8 model.
     *
     * <p>Fabric8 serializes HTTPRouteRule fields alphabetically: {@code backendRefs → filters → matches}.
     * CORS is injected into <strong>each</strong> rule: prepend to an existing {@code filters:} block, or
     * create {@code filters:} before {@code matches:} when absent.</p>
     */
    static String injectCorsFilters(String yaml, String rawCorsYaml) {
        if (rawCorsYaml == null || rawCorsYaml.isBlank()) {
            return yaml;
        }
        // Add 4-space indent so CORS filter items align with other filter items in Fabric8 rule YAML
        String indented = rawCorsYaml.lines()
                .map(line -> line.isBlank() ? "" : "    " + line)
                .collect(Collectors.joining("\n"))
                .stripTrailing() + "\n";

        String filtersMarker = "    filters:\n";
        String matchesMarker = "    matches:\n";
        String rulesHeader = "  rules:\n";
        int rulesIdx = yaml.indexOf(rulesHeader);
        if (rulesIdx < 0) {
            return injectCorsIntoRule(yaml, indented, filtersMarker, matchesMarker);
        }

        String beforeRules = yaml.substring(0, rulesIdx + rulesHeader.length());
        String rulesBody = yaml.substring(rulesIdx + rulesHeader.length());
        if (rulesBody.isEmpty()) {
            return yaml;
        }

        String[] ruleBlocks = rulesBody.split("(?=  - )", -1);
        StringBuilder merged = new StringBuilder(beforeRules.length() + rulesBody.length() + indented.length() * 4);
        merged.append(beforeRules);
        for (String ruleBlock : ruleBlocks) {
            if (!ruleBlock.isEmpty()) {
                merged.append(injectCorsIntoRule(ruleBlock, indented, filtersMarker, matchesMarker));
            }
        }
        return merged.toString();
    }

    private static String injectCorsIntoRule(
            String ruleYaml, String indentedCors, String filtersMarker, String matchesMarker) {
        if (ruleYaml.contains(filtersMarker)) {
            return ruleYaml.replace(matchesMarker, indentedCors + matchesMarker);
        }
        return ruleYaml.replace(matchesMarker, filtersMarker + indentedCors + matchesMarker);
    }

    private static void parseMarkerInto(Map<String, String> annotations, String marker) {
        int colon = marker.indexOf(':');
        if (colon <= 0) {
            LOG.warnf("Ignoring malformed discovery marker (expected key: value): %s", marker);
            return;
        }
        String key = marker.substring(0, colon).trim();
        String value = marker.substring(colon + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            LOG.warnf("Ignoring malformed discovery marker (empty key or value): %s", marker);
            return;
        }
        annotations.put(key, value);
    }

    /** CDI-free fallback for static contributor helpers and isolated unit tests. */
    private static ManifestSerializer resolveSerializer() {
        return new ManifestSerializer();
    }
}
