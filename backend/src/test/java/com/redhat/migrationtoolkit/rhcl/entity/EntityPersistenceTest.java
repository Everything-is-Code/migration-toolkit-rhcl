package com.redhat.migrationtoolkit.rhcl.entity;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

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
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);

        AppSettingsEntity entity = new AppSettingsEntity();
        entity.key = "theme";
        entity.value = "dark";
        entity.persist();
        em.flush();

        AppSettingsEntity loaded = AppSettingsEntity.findById("theme");
        assertNotNull(loaded);
        assertEquals("dark", loaded.value);
        assertNotNull(loaded.updatedAt);
        assertTrue(loaded.updatedAt.isAfter(before) || loaded.updatedAt.isEqual(before));

        OffsetDateTime previousUpdatedAt = loaded.updatedAt;
        loaded.value = "light";
        em.flush();
        em.refresh(loaded);

        assertEquals("light", loaded.value);
        assertTrue(loaded.updatedAt.isAfter(previousUpdatedAt) || loaded.updatedAt.isEqual(previousUpdatedAt));
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

        var previousUpdatedAt = loaded.updatedAt;
        loaded.name = "Acme APIs (updated)";
        em.flush();
        em.refresh(loaded);

        assertTrue(loaded.updatedAt.isAfter(previousUpdatedAt) || loaded.updatedAt.isEqual(previousUpdatedAt));
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
