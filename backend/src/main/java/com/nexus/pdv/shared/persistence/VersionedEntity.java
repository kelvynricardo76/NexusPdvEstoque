package com.nexus.pdv.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Entidade mutável: registra data de atualização e usa versionamento otimista
 * para evitar perda de atualizações concorrentes.
 */
@MappedSuperclass
public abstract class VersionedEntity extends IdentifiedEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onVersionedPrePersist() {
        if (updatedAt == null) {
            updatedAt = getCreatedAt() != null ? getCreatedAt() : Instant.now();
        }
    }

    @PreUpdate
    void onVersionedPreUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
