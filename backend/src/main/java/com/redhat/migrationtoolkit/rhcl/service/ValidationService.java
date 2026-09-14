package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ValidationResult;
import com.redhat.migrationtoolkit.rhcl.dto.ValidationResult.ValidationItem;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@ApplicationScoped
public class ValidationService {

    private static final Logger LOG = Logger.getLogger(ValidationService.class);

    private static final Pattern DOCUMENT_SEPARATOR = Pattern.compile("^---\\s*$");
    private static final Pattern QUOTED_KEY = Pattern.compile("^\"(.+)\"$|^'(.*)'$");

    private static final Set<String> KNOWN_CRDS = Set.of(
            "gateway.networking.k8s.io/v1",
            "gateway.networking.k8s.io/v1beta1",
            "kuadrant.io/v1",
            "kuadrant.io/v1beta2",
            "kuadrant.io/v1alpha1",
            "devportal.kuadrant.io/v1alpha1",
            "networking.istio.io/v1alpha3",
            "networking.istio.io/v1beta1",
            "networking.istio.io/v1",
            "security.istio.io/v1",
            "telemetry.istio.io/v1",
            "telemetry.istio.io/v1alpha1",
            "v1"
    );

    /** Package-visible for unit tests (F6 parse-once). */
    final AtomicInteger parseInvocations = new AtomicInteger();

    public ValidationResult validate(Map<String, String> yamlFiles) {
        ValidationResult result = new ValidationResult();
        result.items = new ArrayList<>();

        for (Map.Entry<String, String> entry : yamlFiles.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();

            if (!filename.endsWith(".yaml")) {
                continue;
            }

            List<Map<String, Object>> docs;
            try {
                docs = loadAllDocs(content);
            } catch (Exception e) {
                result.items.add(new ValidationItem(
                        "YAML Syntax: " + filename, "ERROR", "Invalid YAML: " + e.getMessage()));
                continue;
            }

            result.items.addAll(validateYamlSyntax(filename, docs));
            result.items.addAll(validateDuplicateYamlKeys(filename, content));
            result.items.addAll(validateCrd(filename, docs));
            result.items.addAll(validateNamespace(filename, docs));
            result.items.addAll(validateReferences(filename, content, docs, yamlFiles));
        }

        result.valid = result.items.stream().noneMatch(i -> "ERROR".equals(i.status));
        return result;
    }

    /** Split YAML by --- and return each document as a list. Handles multi-document YAML via loadAll(). */
    private List<Map<String, Object>> loadAllDocs(String content) {
        parseInvocations.incrementAndGet();
        Yaml yaml = new Yaml();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Object obj : yaml.loadAll(content)) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) obj;
                docs.add(doc);
            }
        }
        return docs;
    }

    /** Detect duplicate mapping keys at the same indent (SnakeYAML load silently keeps the last). */
    static List<ValidationItem> validateDuplicateYamlKeys(String filename, String content) {
        List<ValidationItem> items = new ArrayList<>();
        int docIndex = 0;
        for (String doc : splitYamlDocuments(content)) {
            docIndex++;
            Set<String> duplicateKeys = findDuplicateMappingKeys(doc);
            if (duplicateKeys.isEmpty()) {
                continue;
            }
            String label = docIndex > 1 ? filename + " (document " + docIndex + ")" : filename;
            String keys = String.join(", ", duplicateKeys);
            items.add(new ValidationItem(
                    "YAML Structure: " + label,
                    "ERROR",
                    "Duplicate mapping key(s) at the same level: " + keys));
        }
        if (items.isEmpty()) {
            items.add(new ValidationItem("YAML Structure: " + filename, "OK", "No duplicate mapping keys"));
        }
        return items;
    }

    static List<String> splitYamlDocuments(String content) {
        List<String> docs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (DOCUMENT_SEPARATOR.matcher(line).matches()) {
                if (!current.isEmpty()) {
                    docs.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            docs.add(current.toString());
        }
        return docs.isEmpty() ? List.of(content) : docs;
    }

    static Set<String> findDuplicateMappingKeys(String yamlDocument) {
        Set<String> duplicates = new LinkedHashSet<>();
        Map<Integer, Set<String>> keysAtIndent = new HashMap<>();
        for (String line : yamlDocument.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            int indent = leadingSpaces(line);
            keysAtIndent.keySet().removeIf(level -> level > indent);

            MappingKey mappingKey = extractMappingKey(line, indent);
            if (mappingKey == null) {
                continue;
            }
            Set<String> siblings = keysAtIndent.computeIfAbsent(mappingKey.indent, k -> new HashSet<>());
            if (!siblings.add(mappingKey.key)) {
                duplicates.add(mappingKey.key);
            }
        }
        return duplicates;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private record MappingKey(int indent, String key) {}

    private static MappingKey extractMappingKey(String line, int lineIndent) {
        String trimmed = line.substring(lineIndent).trim();
        if (trimmed.startsWith("- ")) {
            String afterDash = trimmed.substring(2).trim();
            int colon = afterDash.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String key = normalizeKey(afterDash.substring(0, colon).trim());
            return key.isEmpty() ? null : new MappingKey(lineIndent + 2, key);
        }
        if (trimmed.startsWith("-")) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String key = normalizeKey(trimmed.substring(0, colon).trim());
        return key.isEmpty() ? null : new MappingKey(lineIndent, key);
    }

    private static String normalizeKey(String raw) {
        var quoted = QUOTED_KEY.matcher(raw);
        if (quoted.matches()) {
            String inner = quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
            return inner != null ? inner : raw;
        }
        return raw;
    }

    private List<ValidationItem> validateYamlSyntax(String filename, List<Map<String, Object>> docs) {
        int count = docs.size();
        String detail = count > 1
                ? "Valid YAML syntax (" + count + " documents)" : "Valid YAML syntax";
        return List.of(new ValidationItem("YAML Syntax: " + filename, "OK", detail));
    }

    @SuppressWarnings("unchecked")
    private List<ValidationItem> validateCrd(String filename, List<Map<String, Object>> docs) {
        List<ValidationItem> items = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String apiVersion = (String) doc.get("apiVersion");
            if (apiVersion == null) {
                continue;
            }
            boolean known = KNOWN_CRDS.stream().anyMatch(apiVersion::startsWith);
            if (known) {
                items.add(new ValidationItem("CRD: " + apiVersion, "OK", "Known CRD group"));
            } else {
                items.add(new ValidationItem("CRD: " + apiVersion, "WARNING",
                        "Unknown CRD - verify it is installed in the cluster"));
            }
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<ValidationItem> validateNamespace(String filename, List<Map<String, Object>> docs) {
        List<ValidationItem> items = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
            if (metadata == null) {
                continue;
            }
            String kind = (String) doc.get("kind");
            String label = kind != null ? filename + " (" + kind + ")" : filename;
            String ns = (String) metadata.get("namespace");
            if (ns == null || ns.isBlank()) {
                items.add(new ValidationItem("Namespace: " + label, "WARNING",
                        "No namespace set, will use default namespace"));
            } else {
                items.add(new ValidationItem("Namespace: " + label, "OK", "Namespace: " + ns));
            }
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<ValidationItem> validateReferences(String filename, String content,
                                                     List<Map<String, Object>> docs,
                                                     Map<String, String> allFiles) {
        List<ValidationItem> items = new ArrayList<>();
        try {
            for (Map<String, Object> doc : docs) {
                String kind = (String) doc.get("kind");

                if ("HTTPRoute".equals(kind)) {
                    Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
                    if (spec != null) {
                        List<Map<String, Object>> parentRefs =
                                (List<Map<String, Object>>) spec.get("parentRefs");
                        if (parentRefs != null) {
                            for (Map<String, Object> ref : parentRefs) {
                                String refName = (String) ref.get("name");
                                boolean gatewayExists = allFiles.containsKey("gateway.yaml");
                                if (gatewayExists) {
                                    items.add(new ValidationItem(
                                            "Reference: Gateway " + refName, "OK",
                                            "Referenced Gateway found in package"));
                                } else {
                                    items.add(new ValidationItem(
                                            "Reference: Gateway " + refName, "WARNING",
                                            "Referenced Gateway not in package - ensure it exists in cluster"));
                                }
                            }
                        }
                    }
                }

                if ("AuthPolicy".equals(kind)) {
                    boolean httprouteExists = allFiles.containsKey("httproute.yaml");
                    if (httprouteExists) {
                        items.add(new ValidationItem(
                                "Reference: AuthPolicy -> HTTPRoute", "OK",
                                "HTTPRoute found in package"));
                    } else {
                        items.add(new ValidationItem(
                                "Reference: AuthPolicy -> HTTPRoute", "WARNING",
                                "HTTPRoute not in package"));
                    }
                }
            }

            if (content.contains(ConversionConstants.CREDENTIAL_PLACEHOLDER)) {
                items.add(new ValidationItem("Secret Values: " + filename, "WARNING",
                        "Contains placeholder values - update before applying"));
            }

        } catch (Exception e) {
            LOG.debugf("Reference validation skipped for %s: %s", filename, e.getMessage());
        }
        return items;
    }
}
