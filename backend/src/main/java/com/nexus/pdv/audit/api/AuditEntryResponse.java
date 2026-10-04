package com.nexus.pdv.audit.api;

import com.nexus.pdv.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID tenantId,
        String actorType,
        UUID actorId,
        String actorName,
        String action,
        String entity,
        String entityId,
        String ip,
        String endpoint,
        String metadata,
        Instant createdAt) {

    public static AuditEntryResponse from(AuditLog log) {
        return new AuditEntryResponse(log.getId(), log.getTenantId(), log.getActorType().name(), log.getActorId(),
                log.getActorName(), log.getAction().name(), log.getEntity(), log.getEntityId(), log.getIp(),
                log.getEndpoint(), log.getMetadata(), log.getCreatedAt());
    }
}
