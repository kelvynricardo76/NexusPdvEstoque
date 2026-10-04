package com.nexus.pdv.billing.domain;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.persistence.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fatura da assinatura SaaS (dinheiro que o tenant paga à Nexus). Não é o financeiro do cliente. */
@Entity
@Table(name = "subscription_invoices")
public class SubscriptionInvoice extends VersionedEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "billing_provider", nullable = false, length = 30)
    private String billingProvider;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    protected SubscriptionInvoice() {
    }

    public SubscriptionInvoice(UUID tenantId, UUID subscriptionId, String description, BigDecimal amount,
            LocalDate dueDate, LocalDate periodStart, LocalDate periodEnd, String billingProvider) {
        this.tenantId = tenantId;
        this.subscriptionId = subscriptionId;
        this.description = description;
        this.amount = amount;
        this.dueDate = dueDate;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.billingProvider = billingProvider;
        this.status = InvoiceStatus.PENDING;
    }

    /** Idempotente: pagar uma fatura já paga não tem efeito. */
    public boolean markPaid(Instant when) {
        if (status == InvoiceStatus.PAID) {
            return false;
        }
        if (status == InvoiceStatus.CANCELED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Fatura cancelada não pode ser paga.");
        }
        this.status = InvoiceStatus.PAID;
        this.paidAt = when;
        return true;
    }

    public void cancel() {
        if (status == InvoiceStatus.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Fatura paga não pode ser cancelada.");
        }
        this.status = InvoiceStatus.CANCELED;
    }

    public void markOverdue() {
        if (status == InvoiceStatus.PENDING) {
            this.status = InvoiceStatus.OVERDUE;
        }
    }

    public void linkExternal(String externalReference) {
        this.externalReference = externalReference;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getBillingProvider() {
        return billingProvider;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
