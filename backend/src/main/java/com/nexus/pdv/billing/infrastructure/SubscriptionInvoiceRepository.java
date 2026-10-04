package com.nexus.pdv.billing.infrastructure;

import com.nexus.pdv.billing.domain.InvoiceStatus;
import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {

    @Query("""
            select i from SubscriptionInvoice i
            where (:tenantId is null or i.tenantId = :tenantId)
              and (:status is null or i.status = :status)
            order by i.dueDate desc, i.createdAt desc
            """)
    Page<SubscriptionInvoice> search(@Param("tenantId") UUID tenantId, @Param("status") InvoiceStatus status,
            Pageable pageable);

    Optional<SubscriptionInvoice> findByBillingProviderAndExternalReference(String provider, String externalReference);

    List<SubscriptionInvoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);

    List<SubscriptionInvoice> findByTenantIdOrderByDueDateDesc(UUID tenantId);
}
