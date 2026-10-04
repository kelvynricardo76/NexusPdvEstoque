package com.nexus.pdv.audit.application;

import com.nexus.pdv.audit.api.AuditEntryResponse;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.audit.infrastructure.AuditLogRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta da trilha de auditoria. O tenant é sempre informado pelo chamador a partir do contexto autenticado. */
@Service
public class AuditQueryService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditQueryService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> searchTenant(UUID tenantId, AuditAction action, String entity, UUID actorId,
            LocalDate from, LocalDate to, ZoneId zone, Pageable pageable) {
        Instant[] range = range(from, to, zone);
        return repository.searchTenant(tenantId, action, entity, actorId, range[0], range[1], pageable)
                .map(AuditEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> searchPlatform(UUID tenantId, AuditAction action, LocalDate from, LocalDate to,
            Pageable pageable) {
        Instant[] range = range(from, to, ZoneId.of("UTC"));
        return repository.searchPlatform(tenantId, action, range[0], range[1], pageable).map(AuditEntryResponse::from);
    }

    private Instant[] range(LocalDate from, LocalDate to, ZoneId zone) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Período inválido (máximo de " + MAX_RANGE_DAYS + " dias).");
        }
        return new Instant[] {start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant()};
    }
}
