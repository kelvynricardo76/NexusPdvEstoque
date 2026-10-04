package com.nexus.pdv.platform.application;

import com.nexus.pdv.platform.api.SuperAdminDtos.TenantUsage;
import com.nexus.pdv.user.domain.UserStatus;
import com.nexus.pdv.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumo de recursos de um tenant (visão da plataforma, contexto root). */
@Service
public class TenantUsageService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public TenantUsageService(UserRepository userRepository, EntityManager entityManager, Clock clock) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TenantUsage usageOf(UUID tenantId) {
        long users = userRepository.countByTenantAndStatus(tenantId, UserStatus.ACTIVE);
        long products = entityManager.createQuery(
                        "select count(p) from Product p where p.tenantId = :tenantId", Long.class)
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        Instant monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long sales = entityManager.createQuery(
                        "select count(s) from Sale s where s.tenantId = :tenantId and s.createdAt >= :from", Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("from", monthStart)
                .getSingleResult();
        return new TenantUsage(users, products, sales);
    }
}
