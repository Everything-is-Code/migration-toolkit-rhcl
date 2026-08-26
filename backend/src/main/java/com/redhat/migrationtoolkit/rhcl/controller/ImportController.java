package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.exception.ImportParseException;
import com.redhat.migrationtoolkit.rhcl.exception.ValidationException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Path("/api/import")
@Tag(name = "Import", description = "Import ZIP packages")
public class ImportController {

    private static final Logger LOG = Logger.getLogger(ImportController.class);

    @POST
    @Path("/zip")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload and extract a ZIP file, returning YAML contents")
    public Response uploadZip(@RestForm("file") FileUpload fileUpload) {
        if (fileUpload == null) {
            LOG.warnf("Import rejected: no file uploaded");
            throw new ValidationException("File upload is required");
        }

        Map<String, String> yamlFiles = new HashMap<>();

        try (InputStream is = java.nio.file.Files.newInputStream(fileUpload.uploadedFile());
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String lower = name.toLowerCase();
                if (!entry.isDirectory() && (lower.endsWith(".yaml") || lower.endsWith(".yml")
                        || lower.endsWith("readme.md") || lower.endsWith("readme.txt") || lower.equals("readme"))) {
                    String basename = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                    byte[] content = zis.readAllBytes();
                    yamlFiles.put(basename, new String(content, StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }

        } catch (IOException e) {
            LOG.warnf(e, "Failed to parse ZIP file: %s", e.getMessage());
            throw new ImportParseException("Failed to parse ZIP file", e);
        }

        boolean hasYaml = yamlFiles.keySet().stream()
                .anyMatch(k -> k.endsWith(".yaml") || k.endsWith(".yml"));
        if (!hasYaml) {
            LOG.warnf("Import rejected: no YAML files found in ZIP");
            throw ImportParseException.noYaml();
        }

        return Response.ok(Map.of(
                "files", yamlFiles,
                "count", yamlFiles.size()
        )).build();
    }
}
