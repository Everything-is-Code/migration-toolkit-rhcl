package com.redhat.migrationtoolkit.rhcl.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "app_settings")
public class AppSettingsEntity extends PanacheEntityBase {

    @Id
    @Column(name = "\"key\"")
    public String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String value;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
