package com.nexus.pdv.billing.infrastructure;

import com.nexus.pdv.billing.domain.BillingWebhookEventRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEventRecord, UUID> {

    boolean existsByProviderAndEventId(String provider, String eventId);
}
