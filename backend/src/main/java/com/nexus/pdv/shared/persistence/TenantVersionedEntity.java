package com.nexus.pdv.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Entidade mutável pertencente a um tenant (ver {@link TenantOwnedEntity}).
 */
@MappedSuperclass
public abstract class TenantVersionedEntity extends VersionedEntity {

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
