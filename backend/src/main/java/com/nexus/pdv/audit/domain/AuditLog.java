package com.nexus.pdv.audit.domain;

import com.nexus.pdv.shared.persistence.IdentifiedEntity;
import com.nexus.pdv.shared.security.PrincipalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Registro de auditoria (imutável). Não usa {@code @TenantId} porque também registra ações da
 * plataforma (tenant nulo); consultas de tenant filtram explicitamente por {@code tenantId}.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends IdentifiedEntity {

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", length = 120, updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Column(name = "entity", length = 60, updatable = false)
    private String entity;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    @Column(name = "ip", length = 64, updatable = false)
    private String ip;

    @Column(name = "endpoint", length = 200, updatable = false)
    private String endpoint;

    @Column(name = "metadata", length = 4000, updatable = false)
    private String metadata;

    protected AuditLog() {
    }

    public AuditLog(UUID tenantId, ActorType actorType, UUID actorId, String actorName, AuditAction action,
            String entity, String entityId, String ip, String endpoint, String metadata) {
        this.tenantId = tenantId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.ip = ip;
        this.endpoint = endpoint;
        this.metadata = metadata;
    }

    public enum ActorType {
        PLATFORM_ADMIN,
        TENANT_USER,
        SYSTEM,
        ANONYMOUS;

        public static ActorType of(PrincipalType type) {
            return type == PrincipalType.PLATFORM_ADMIN ? PLATFORM_ADMIN : TENANT_USER;
        }
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getIp() {
        return ip;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getMetadata() {
        return metadata;
    }
}
