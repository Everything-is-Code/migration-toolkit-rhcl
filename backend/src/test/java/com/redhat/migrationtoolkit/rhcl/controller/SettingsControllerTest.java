package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.entity.AppSettingsEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class SettingsControllerTest {

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void cleanUp() {
        em.createQuery("DELETE FROM AppSettingsEntity").executeUpdate();
    }

    @Test
    void get_notFound_returns404Envelope() {
        given()
                .when().get("/api/settings/nonexistent-key")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("SETTINGS_NOT_FOUND"));
    }

    @Test
    void put_missingValue_returns400Envelope() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().put("/api/settings/some-key")
                .then()
                .statusCode(400)
                .body("error.code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void get_found_returns200() {
        persistSetting("timezone", "UTC");

        given()
                .when().get("/api/settings/timezone")
                .then()
                .statusCode(200)
                .body("key", equalTo("timezone"))
                .body("value", equalTo("UTC"));
    }

    @Test
    void put_createsOrUpdates_returns200() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"value\":\"Asia/Tokyo\"}")
                .when().put("/api/settings/timezone")
                .then()
                .statusCode(200)
                .body("key", equalTo("timezone"))
                .body("value", equalTo("Asia/Tokyo"));
    }

    @Transactional
    void persistSetting(String key, String value) {
        AppSettingsEntity entity = new AppSettingsEntity();
        entity.key = key;
        entity.value = value;
        entity.persist();
    }
}
