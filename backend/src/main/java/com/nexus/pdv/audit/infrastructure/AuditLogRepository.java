package com.nexus.pdv.audit.infrastructure;

import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Auditoria de um tenant — o tenantId vem SEMPRE do contexto autenticado. */
    @Query("""
            select a from AuditLog a
            where a.tenantId = :tenantId
              and (:action is null or a.action = :action)
              and (:entity is null or a.entity = :entity)
              and (:actorId is null or a.actorId = :actorId)
              and a.createdAt >= :from and a.createdAt < :to
            order by a.createdAt desc
            """)
    Page<AuditLog> searchTenant(@Param("tenantId") UUID tenantId, @Param("action") AuditAction action,
            @Param("entity") String entity, @Param("actorId") UUID actorId, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /** Auditoria global (Super Admin). */
    @Query("""
            select a from AuditLog a
            where (:tenantId is null or a.tenantId = :tenantId)
              and (:action is null or a.action = :action)
              and a.createdAt >= :from and a.createdAt < :to
            order by a.createdAt desc
            """)
    Page<AuditLog> searchPlatform(@Param("tenantId") UUID tenantId, @Param("action") AuditAction action,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
