package com.nexus.pdv.subscription.infrastructure;

import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

    Optional<TenantSubscription> findByTenantId(UUID tenantId);

    List<TenantSubscription> findByTenantIdIn(Collection<UUID> tenantIds);

    long countByPlanId(UUID planId);

    long countByStatus(SubscriptionStatus status);

    @Query("""
            select s from TenantSubscription s
            where (:status is null or s.status = :status)
              and (:planCode is null or s.plan.code = :planCode)
            """)
    Page<TenantSubscription> search(@Param("status") SubscriptionStatus status, @Param("planCode") String planCode,
            Pageable pageable);
}
