package com.nexus.pdv.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Registro de webhook processado (garante idempotência por provedor + id do evento). */
@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEventRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 30, updatable = false)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 120, updatable = false)
    private String eventId;

    @Column(name = "event_type", length = 60, updatable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected BillingWebhookEventRecord() {
    }

    public BillingWebhookEventRecord(String provider, String eventId, String eventType, Instant receivedAt) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
    }
}
