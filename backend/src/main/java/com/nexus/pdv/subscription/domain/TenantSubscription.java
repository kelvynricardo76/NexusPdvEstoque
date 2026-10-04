package com.nexus.pdv.subscription.domain;

import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.shared.persistence.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Relação comercial entre tenant e plano. Independente do gateway de pagamento: o provedor
 * aparece apenas como referência ({@code billingProvider}, {@code externalReference}).
 */
@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription extends VersionedEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 10)
    private BillingCycle billingCycle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    @Column(name = "current_period_start")
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "billing_provider", nullable = false, length = 30)
    private String billingProvider;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    protected TenantSubscription() {
    }

    public TenantSubscription(UUID tenantId, Plan plan, BillingCycle billingCycle, LocalDate startDate,
            LocalDate trialEndDate, String billingProvider) {
        this.tenantId = tenantId;
        this.plan = plan;
        this.billingCycle = billingCycle;
        this.startDate = startDate;
        this.billingProvider = billingProvider;
        if (trialEndDate != null) {
            this.status = SubscriptionStatus.TRIAL;
            this.trialEndDate = trialEndDate;
        } else {
            this.status = SubscriptionStatus.ACTIVE;
            this.currentPeriodStart = startDate;
            this.currentPeriodEnd = billingCycle == BillingCycle.ANNUAL ? startDate.plusYears(1) : startDate.plusMonths(1);
        }
    }

    /** A assinatura permite operação? TRIAL vencido, SUSPENDED e CANCELED bloqueiam. */
    public boolean allowsOperation(LocalDate today) {
        return switch (status) {
            case TRIAL -> trialEndDate == null || !today.isAfter(trialEndDate);
            case ACTIVE, PAST_DUE -> true;
            case SUSPENDED, CANCELED -> false;
        };
    }

    /** Receita mensal recorrente equivalente desta assinatura (0 se não pagante). */
    public BigDecimal monthlyRecurringRevenue() {
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            return BigDecimal.ZERO;
        }
        return billingCycle == BillingCycle.ANNUAL
                ? plan.getAnnualPrice().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                : plan.getMonthlyPrice();
    }

    public void changePlan(Plan newPlan, BillingCycle newCycle) {
        this.plan = newPlan;
        this.billingCycle = newCycle;
    }

    public void updateTerms(SubscriptionStatus status, LocalDate trialEndDate, LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd, boolean cancelAtPeriodEnd) {
        this.status = status;
        this.trialEndDate = trialEndDate;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public void changeStatus(SubscriptionStatus status) {
        this.status = status;
    }

    /** Registra pagamento de um período: ativa e avança a vigência. */
    public void renewPeriod(LocalDate periodStart) {
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = billingCycle == BillingCycle.ANNUAL ? periodStart.plusYears(1) : periodStart.plusMonths(1);
    }

    public void linkExternal(String provider, String externalReference) {
        this.billingProvider = provider;
        this.externalReference = externalReference;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getTrialEndDate() {
        return trialEndDate;
    }

    public LocalDate getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public String getBillingProvider() {
        return billingProvider;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
