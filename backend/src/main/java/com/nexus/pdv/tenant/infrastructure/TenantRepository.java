package com.nexus.pdv.tenant.infrastructure;

import com.nexus.pdv.tenant.domain.Tenant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Trava a linha do tenant até o fim da transação. Serializa operações que verificam limites
     * do plano (ex.: criação de usuários/produtos), evitando que requisições concorrentes
     * ultrapassem o limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> lockById(@Param("id") UUID id);

    long countByCreatedAtAfter(Instant instant);
}
