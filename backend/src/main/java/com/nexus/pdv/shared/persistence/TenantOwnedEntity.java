package com.nexus.pdv.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Entidade imutável pertencente a um tenant. O Hibernate filtra automaticamente
 * todas as consultas pelo tenant do contexto autenticado e preenche {@code tenant_id}
 * na inserção (ver {@link TenantIdentifierResolver}).
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends IdentifiedEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    /** Atribuição explícita — só aceita pelo Hibernate em contexto de sistema (root). */
    protected void assignTenant(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
