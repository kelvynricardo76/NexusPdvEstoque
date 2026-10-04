package com.nexus.pdv.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base de todas as entidades: identificador UUID ordenado por tempo e data de criação.
 * Igualdade por identificador (entidades transientes nunca são iguais entre si).
 */
@MappedSuperclass
public abstract class IdentifiedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onIdentifiedPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Permite retroagir a data de criação (usado apenas por seeds de desenvolvimento). */
    public void backdateCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        return id != null && id.equals(((IdentifiedEntity) other).id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
