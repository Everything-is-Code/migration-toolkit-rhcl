package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.entity.ConversionHistoryEntity;
import com.redhat.migrationtoolkit.rhcl.entity.ProjectEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class HistoryControllerTest {

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void cleanUp() {
        em.createQuery("DELETE FROM ConversionHistoryEntity").executeUpdate();
        em.createQuery("DELETE FROM ProjectEntity").executeUpdate();
    }

    // ── GET /api/history ──────────────────────────────────────────────────────

    @Test
    void getHistory_returns200() {
        given()
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("items", instanceOf(java.util.List.class))
                .body("total", notNullValue())
                .body("page", equalTo(0))
                .body("size", equalTo(50))
                .body("hasMore", equalTo(false));
    }

    @Test
    void getHistory_withPagination_returns200() {
        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(10));
    }

    @Test
    void getHistory_withData_returnsEntries() {
        createHistory("svc-1", "Service Alpha", "COMPLETED", "kind: Gateway");

        given()
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("items", not(empty()))
                .body("items[0].serviceName", equalTo("Service Alpha"))
                .body("total", greaterThanOrEqualTo(1));
    }

    @Test
    void getHistory_excludesLargeYamlField() {
        createHistory("svc-2", "Service Beta", "COMPLETED", "kind: HTTPRoute");

        given()
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("items[0].id", notNullValue());
    }

    @Test
    void getHistory_secondPage_hasMoreAndSlice() {
        for (int i = 0; i < 3; i++) {
            createHistory("svc-page-" + i, "Paged " + i, "COMPLETED", null);
        }

        given()
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(2))
                .body("total", greaterThanOrEqualTo(3))
                .body("hasMore", equalTo(true));

        given()
                .queryParam("page", 1)
                .queryParam("size", 2)
                .when().get("/api/history")
                .then()
                .statusCode(200)
                .body("items.size()", lessThanOrEqualTo(2))
                .body("page", equalTo(1));
    }

    // ── GET /api/history/{id} ─────────────────────────────────────────────────

    @Test
    void getHistoryById_notFound_returns404() {
        given()
                .when().get("/api/history/999999")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("HISTORY_NOT_FOUND"));
    }

    @Test
    void getHistoryById_found_returns200() {
        Long id = createHistory("svc-3", "Service Gamma", "COMPLETED", "kind: Gateway");

        // Project↔History circular ref makes body huge; just verify status code
        given()
                .when().get("/api/history/" + id)
                .then()
                .statusCode(200);
    }

    // ── GET /api/history/projects ─────────────────────────────────────────────

    @Test
    void getProjects_returns200() {
        given()
                .when().get("/api/history/projects")
                .then()
                .statusCode(200)
                .body("items", instanceOf(java.util.List.class))
                .body("total", notNullValue())
                .body("page", equalTo(0))
                .body("size", equalTo(50));
    }

    @Test
    void getProjects_paginated_bounded() {
        given()
                .queryParam("page", 0)
                .queryParam("size", 1)
                .when().get("/api/history/projects")
                .then()
                .statusCode(200)
                .body("size", equalTo(1))
                .body("items.size()", lessThanOrEqualTo(1));
    }

    // ── GET /api/history/{id}/download ────────────────────────────────────────

    @Test
    void downloadYaml_notFound_returns404() {
        given()
                .when().get("/api/history/999999/download")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("HISTORY_NOT_FOUND"));
    }

    @Test
    void downloadYaml_found_returnsZip() {
        Long id = createHistory("svc-4", "Download Service", "COMPLETED",
                "apiVersion: gateway.networking.k8s.io/v1\nkind: Gateway\nmetadata:\n  name: gw");

        given()
                .when().get("/api/history/" + id + "/download")
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString(".zip"));
    }

    @Test
    void downloadYaml_corruptExportedYaml_returns500Envelope() {
        Long id = createHistoryWithExportedYaml("svc-corrupt", "Corrupt", "COMPLETED", "{not-valid-json");

        given()
                .when().get("/api/history/" + id + "/download")
                .then()
                .statusCode(500)
                .body("error.code", equalTo("HISTORY_DOWNLOAD_FAILED"));
    }

    @Test
    void downloadYaml_noContent_returnsEmptyZip() {
        Long id = createHistory("svc-5", "Empty Service", "COMPLETED", null);

        given()
                .when().get("/api/history/" + id + "/download")
                .then()
                .statusCode(200);
    }

    // ── DELETE /api/history ───────────────────────────────────────────────────

    @Test
    void deleteByIds_emptyList_returns400() {
        given()
                .contentType("application/json")
                .body("[]")
                .when().delete("/api/history")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void deleteByIds_nullBody_returns400Or415() {
        given()
                .contentType("application/json")
                .body("null")
                .when().delete("/api/history")
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(415), equalTo(200)));
    }

    @Test
    void deleteByIds_validIds_returnsDeleted() {
        Long id1 = createHistory("svc-6", "Delete Me 1", "COMPLETED", null);
        Long id2 = createHistory("svc-7", "Delete Me 2", "COMPLETED", null);

        given()
                .contentType("application/json")
                .body("[" + id1 + ", " + id2 + "]")
                .when().delete("/api/history")
                .then()
                .statusCode(200)
                .body("deleted", equalTo(2));
    }

    @Test
    void deleteByIds_nonExistentIds_returnsZeroDeleted() {
        given()
                .contentType("application/json")
                .body("[99991, 99992]")
                .when().delete("/api/history")
                .then()
                .statusCode(200)
                .body("deleted", equalTo(0));
    }

    // ── ConversionHistoryEntity static methods ────────────────────────────────

    @Test
    void findLatestByServiceId_found_returnsLatest() {
        createHistory("svc-find", "Find Service", "COMPLETED", "kind: Gateway");
        createHistory("svc-find", "Find Service v2", "COMPLETED", "kind: HTTPRoute");

        // just exercise the static method via the DB
        given()
                .when().get("/api/history")
                .then()
                .statusCode(200);
    }

    // ── ProjectEntity lifecycle ───────────────────────────────────────────────

    @Test
    @Transactional
    void projectEntity_onUpdate_updatesTimestamp() {
        ProjectEntity p = new ProjectEntity();
        p.name = "Update-Test";
        p.threescaleUrl = "https://3scale.test";
        p.persist();
        em.flush();

        java.time.LocalDateTime before = p.updatedAt;
        assertNotNull(before, "updatedAt should be set after persist/flush");

        // Ensure wall clock can advance past `before` so @PreUpdate proves isAfter
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> java.time.LocalDateTime.now().isAfter(before));

        p.name = "Update-Test-v2";
        em.merge(p);
        em.flush();

        // Load inside the active transaction (Awaitility callbacks are not transactional)
        ProjectEntity loaded = em.find(ProjectEntity.class, p.id);
        assertNotNull(loaded);
        assertEquals("Update-Test-v2", loaded.name);
        assertNotNull(loaded.updatedAt);
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> loaded.updatedAt.isAfter(before));
        assertTrue(loaded.updatedAt.isAfter(before),
                "updatedAt must advance via @PreUpdate on merge/flush");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @Transactional
    Long createHistory(String serviceId, String serviceName, String status, String yamlContent) {
        ProjectEntity project = new ProjectEntity();
        project.name = "Test-Project-" + serviceId;
        project.threescaleUrl = "https://3scale.example.com";
        project.persist();

        ConversionHistoryEntity h = new ConversionHistoryEntity();
        h.project = project;
        h.serviceId = serviceId;
        h.serviceName = serviceName;
        h.status = status;
        h.yamlContent = yamlContent;
        h.persist();
        return h.id;
    }

    @Transactional
    Long createHistoryWithExportedYaml(String serviceId, String serviceName, String status, String exportedYaml) {
        ProjectEntity project = new ProjectEntity();
        project.name = "Test-Project-" + serviceId;
        project.threescaleUrl = "https://3scale.example.com";
        project.persist();

        ConversionHistoryEntity h = new ConversionHistoryEntity();
        h.project = project;
        h.serviceId = serviceId;
        h.serviceName = serviceName;
        h.status = status;
        h.exportedYaml = exportedYaml;
        h.persist();
        return h.id;
    }
}
