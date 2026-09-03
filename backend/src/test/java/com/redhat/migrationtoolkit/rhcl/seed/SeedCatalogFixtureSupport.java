package com.redhat.migrationtoolkit.rhcl.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Loads seed catalog, expectations, and export fixtures from {@code testdata/} at repo root. */
public final class SeedCatalogFixtureSupport {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private SeedCatalogFixtureSupport() {
    }

    public static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (cwd.endsWith("backend")) {
            return cwd.getParent();
        }
        return cwd;
    }

    public static Path seedDir() {
        return repoRoot().resolve("testdata/seed");
    }

    public static Path exportsDir() {
        return repoRoot().resolve("testdata/exports");
    }

    public static Path exportPath(String systemName) {
        return exportsDir().resolve(systemName + ".json");
    }

    @SuppressWarnings("unchecked")
    public static Set<String> loadCatalogSystemNames() {
        try {
            Path catalog = seedDir().resolve("catalog.yaml");
            Map<String, Object> root = YAML.readValue(catalog.toFile(), Map.class);
            List<Map<String, Object>> products = (List<Map<String, Object>>) root.get("products");
            if (products == null) {
                throw new IllegalStateException("catalog.yaml missing products list");
            }
            Set<String> names = new TreeSet<>();
            for (Map<String, Object> product : products) {
                Object systemName = product.get("system_name");
                if (systemName instanceof String name && !name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SeedExpectationsDocument loadExpectations() {
        try {
            Path file = seedDir().resolve("expectations.yaml");
            return YAML.readValue(file.toFile(), SeedExpectationsDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Set<String> loadExpectationSystemNames() {
        SeedExpectationsDocument doc = loadExpectations();
        if (doc.products == null || doc.products.isEmpty()) {
            throw new IllegalStateException("expectations.yaml missing products");
        }
        return new TreeSet<>(doc.products.keySet());
    }

    public static Set<String> missingExportFiles(Set<String> systemNames) {
        return systemNames.stream()
                .filter(name -> !Files.isRegularFile(exportPath(name)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static ApiService loadExport(String systemName) {
        try {
            Path file = exportPath(systemName);
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("Missing export fixture: " + file);
            }
            return JSON.readValue(file.toFile(), ApiService.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeedExpectationsDocument {
        public int version;
        public String defaultNamespace;
        public Map<String, ProductExpectation> products;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductExpectation {
        public String productLabel;
        public String namespace;
        public Map<String, FileExpectation> files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileExpectation {
        public List<String> mustContain;
        public List<String> mustNotContain;
    }
}
