package com.nexus.pdv.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.audit.domain.AuditLog;
import com.nexus.pdv.audit.domain.AuditLog.ActorType;
import com.nexus.pdv.audit.infrastructure.AuditLogRepository;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.web.ClientRequestInfo;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de auditoria. Participa da transação de negócio (se ela for revertida, o registro
 * também é). Metadados passam por sanitização: chaves sensíveis são descartadas.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_METADATA_LENGTH = 4000;
    private static final Set<String> SENSITIVE_KEY_PARTS =
            Set.of("password", "senha", "token", "secret", "card", "cartao", "cvv", "hash", "authorization", "cookie");

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Registra ação do usuário autenticado atual. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entity, Object entityId, Map<String, ?> metadata) {
        AuthenticatedUser user = CurrentUser.find().orElse(null);
        if (user == null) {
            save(null, ActorType.SYSTEM, null, null, action, entity, entityId, metadata);
        } else {
            save(user.tenantId(), ActorType.of(user.type()), user.id(), user.name(), action, entity, entityId, metadata);
        }
    }

    public void record(AuditAction action, String entity, Object entityId) {
        record(action, entity, entityId, Map.of());
    }

    /** Registra ação de um ator explícito (ex.: login, antes de existir sessão). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordFor(AuthenticatedUser actor, AuditAction action, String entity, Object entityId,
            Map<String, ?> metadata) {
        save(actor.tenantId(), ActorType.of(actor.type()), actor.id(), actor.name(), action, entity, entityId, metadata);
    }

    /** Registra ação de um ator explícito em transação própria (persiste mesmo se o fluxo falhar). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(UUID tenantId, ActorType actorType, UUID actorId, String actorName,
            AuditAction action, String entity, Object entityId, Map<String, ?> metadata) {
        save(tenantId, actorType, actorId, actorName, action, entity, entityId, metadata);
    }

    /** Registra ação da plataforma sobre um tenant (tenantId explícito, ator = Super Admin atual). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordOnTenant(UUID tenantId, AuditAction action, String entity, Object entityId,
            Map<String, ?> metadata) {
        AuthenticatedUser user = CurrentUser.find().orElse(null);
        save(tenantId, user == null ? ActorType.SYSTEM : ActorType.of(user.type()), user == null ? null : user.id(),
                user == null ? null : user.name(), action, entity, entityId, metadata);
    }

    private void save(UUID tenantId, ActorType actorType, UUID actorId, String actorName, AuditAction action,
            String entity, Object entityId, Map<String, ?> metadata) {
        ClientRequestInfo request = ClientRequestInfo.current();
        repository.save(new AuditLog(tenantId, actorType, actorId, actorName, action, entity,
                entityId == null ? null : entityId.toString(), request.ip(), request.endpoint(),
                serialize(metadata)));
    }

    String serialize(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!isSensitive(key)) {
                safe.put(key, value);
            }
        });
        if (safe.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(safe);
            return json.length() > MAX_METADATA_LENGTH ? json.substring(0, MAX_METADATA_LENGTH) : json;
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar metadados de auditoria: {}", ex.getOriginalMessage());
            return null;
        }
    }

    static boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
