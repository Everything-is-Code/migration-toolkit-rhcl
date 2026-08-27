package com.redhat.migrationtoolkit.rhcl.entity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EntityPersistenceTest {

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void cleanUp() {
        em.createQuery("DELETE FROM ConversionHistoryEntity").executeUpdate();
        em.createQuery("DELETE FROM ProjectEntity").executeUpdate();
        em.createQuery("DELETE FROM AppSettingsEntity").executeUpdate();
    }

    @Test
    @Transactional
    void appSettingsEntity_persistAndUpdate() {
        AppSettingsEntity entity = new AppSettingsEntity();
        entity.key = "theme";
        entity.value = "dark";
        entity.persist();
        em.flush();

        AppSettingsEntity loaded = AppSettingsEntity.findById("theme");
        assertNotNull(loaded);
        assertEquals("dark", loaded.value);
        assertNotNull(loaded.updatedAt);

        OffsetDateTime before = loaded.updatedAt;
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> OffsetDateTime.now(ZoneOffset.UTC).isAfter(before));

        loaded.value = "light";
        em.merge(loaded);
        em.flush();

        AppSettingsEntity refreshed = em.find(AppSettingsEntity.class, "theme");
        assertNotNull(refreshed);
        assertEquals("light", refreshed.value);
        assertNotNull(refreshed.updatedAt);
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> refreshed.updatedAt.isAfter(before));
        assertTrue(refreshed.updatedAt.isAfter(before),
                "updatedAt must advance via @PreUpdate on merge/flush");
    }

    @Test
    @Transactional
    void projectEntity_persistAndOnUpdate() {
        ProjectEntity project = new ProjectEntity();
        project.name = "Acme APIs";
        project.threescaleUrl = "https://3scale.example.com";
        project.tenant = "acme";
        project.persist();
        em.flush();

        ProjectEntity loaded = ProjectEntity.findById(project.id);
        assertNotNull(loaded);
        assertEquals("Acme APIs", loaded.name);
        assertNotNull(loaded.createdAt);
        assertNotNull(loaded.updatedAt);

        LocalDateTime before = loaded.updatedAt;
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> LocalDateTime.now().isAfter(before));

        loaded.name = "Acme APIs (updated)";
        em.merge(loaded);
        em.flush();

        ProjectEntity refreshed = em.find(ProjectEntity.class, project.id);
        assertNotNull(refreshed);
        assertEquals("Acme APIs (updated)", refreshed.name);
        assertNotNull(refreshed.updatedAt);
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> refreshed.updatedAt.isAfter(before));
        assertTrue(refreshed.updatedAt.isAfter(before),
                "updatedAt must advance via @PreUpdate on merge/flush");
    }

    @Test
    @Transactional
    void conversionHistoryEntity_findLatestByServiceId() {
        ProjectEntity project = new ProjectEntity();
        project.name = "History Project";
        project.persist();

        ConversionHistoryEntity older = new ConversionHistoryEntity();
        older.project = project;
        older.serviceId = "svc-1";
        older.serviceName = "First";
        older.status = "COMPLETED";
        older.createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        older.persist();

        ConversionHistoryEntity latest = new ConversionHistoryEntity();
        latest.project = project;
        latest.serviceId = "svc-1";
        latest.serviceName = "Latest";
        latest.status = "COMPLETED";
        latest.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        latest.persist();

        ConversionHistoryEntity other = new ConversionHistoryEntity();
        other.project = project;
        other.serviceId = "svc-2";
        other.serviceName = "Other";
        other.status = "COMPLETED";
        other.persist();

        em.flush();

        ConversionHistoryEntity found = ConversionHistoryEntity.findLatestByServiceId("svc-1");
        assertNotNull(found);
        assertEquals("Latest", found.serviceName);
        assertEquals(project.id, found.project.id);
    }
}
