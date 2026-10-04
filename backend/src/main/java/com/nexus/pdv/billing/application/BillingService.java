package com.nexus.pdv.billing.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.billing.application.BillingProvider.BillingWebhookEvent;
import com.nexus.pdv.billing.domain.BillingWebhookEventRecord;
import com.nexus.pdv.billing.domain.InvoiceStatus;
import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import com.nexus.pdv.billing.infrastructure.BillingWebhookEventRepository;
import com.nexus.pdv.billing.infrastructure.SubscriptionInvoiceRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cobrança da assinatura SaaS. Não se relaciona com o financeiro operacional do tenant. */
@Service
public class BillingService {

    private final SubscriptionInvoiceRepository invoiceRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final List<BillingProvider> providers;
    private final AuditService auditService;
    private final Clock clock;

    public BillingService(SubscriptionInvoiceRepository invoiceRepository,
            BillingWebhookEventRepository webhookEventRepository, TenantSubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository, List<BillingProvider> providers, AuditService auditService, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.providers = providers;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionInvoice> search(UUID tenantId, InvoiceStatus status, Pageable pageable) {
        return invoiceRepository.search(tenantId, status, pageable);
    }

    /** Emite a fatura do próximo período. Valor padrão = preço do plano no ciclo da assinatura. */
    @Transactional
    public SubscriptionInvoice issueInvoice(UUID tenantId, BigDecimal amount, LocalDate dueDate, String description) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        BigDecimal value = amount != null ? amount
                : subscription.getBillingCycle() == BillingCycle.ANNUAL
                        ? subscription.getPlan().getAnnualPrice()
                        : subscription.getPlan().getMonthlyPrice();
        LocalDate periodStart = subscription.getCurrentPeriodEnd() != null
                ? subscription.getCurrentPeriodEnd()
                : LocalDate.now(clock);
        LocalDate periodEnd = subscription.getBillingCycle() == BillingCycle.ANNUAL
                ? periodStart.plusYears(1)
                : periodStart.plusMonths(1);
        String text = description != null && !description.isBlank() ? description
                : "Assinatura " + subscription.getPlan().getName() + " — " + periodStart + " a " + periodEnd;
        BillingProvider provider = provider(subscription.getBillingProvider());
        SubscriptionInvoice invoice = invoiceRepository.save(new SubscriptionInvoice(tenantId, subscription.getId(),
                text, value, dueDate != null ? dueDate : periodStart, periodStart, periodEnd, provider.code()));
        provider.issueInvoice(tenant, invoice).ifPresent(invoice::linkExternal);
        auditService.recordOnTenant(tenantId, AuditAction.BILLING, "SubscriptionInvoice", invoice.getId(),
                Map.of("operation", "issue", "amount", value));
        return invoice;
    }

    /** Baixa manual (idempotente). Reativa a assinatura e avança a vigência. */
    @Transactional
    public SubscriptionInvoice markPaid(UUID invoiceId) {
        SubscriptionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        applyPayment(invoice);
        return invoice;
    }

    @Transactional
    public SubscriptionInvoice cancel(UUID invoiceId) {
        SubscriptionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        invoice.cancel();
        auditService.recordOnTenant(invoice.getTenantId(), AuditAction.BILLING, "SubscriptionInvoice", invoice.getId(),
                Map.of("operation", "cancel"));
        return invoice;
    }

    /**
     * Processa webhook do provedor com idempotência: eventos repetidos são ignorados.
     *
     * @return {@code true} se o evento foi processado agora; {@code false} se já havia sido
     */
    @Transactional
    public boolean handleWebhook(String providerCode, String payload, Map<String, String> headers) {
        BillingProvider provider = provider(providerCode);
        BillingWebhookEvent event = provider.parseWebhook(payload, headers)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (webhookEventRepository.existsByProviderAndEventId(provider.code(), event.eventId())) {
            return false;
        }
        // A constraint única (provider, event_id) protege contra entregas concorrentes.
        webhookEventRepository.saveAndFlush(new BillingWebhookEventRecord(provider.code(), event.eventId(),
                event.type(), clock.instant()));
        invoiceRepository.findByBillingProviderAndExternalReference(provider.code(), event.externalInvoiceReference())
                .ifPresent(invoice -> {
                    if (BillingWebhookEvent.INVOICE_PAID.equals(event.type())) {
                        applyPayment(invoice);
                    } else if (BillingWebhookEvent.INVOICE_OVERDUE.equals(event.type())) {
                        invoice.markOverdue();
                    }
                });
        return true;
    }

    private void applyPayment(SubscriptionInvoice invoice) {
        if (!invoice.markPaid(clock.instant())) {
            return;
        }
        subscriptionRepository.findById(invoice.getSubscriptionId()).ifPresent(subscription -> {
            if (subscription.getStatus() != SubscriptionStatus.CANCELED) {
                subscription.renewPeriod(invoice.getPeriodStart() != null ? invoice.getPeriodStart() : LocalDate.now(clock));
            }
        });
        auditService.recordOnTenant(invoice.getTenantId(), AuditAction.BILLING, "SubscriptionInvoice", invoice.getId(),
                Map.of("operation", "paid", "amount", invoice.getAmount()));
    }

    private BillingProvider provider(String code) {
        return providers.stream()
                .filter(provider -> provider.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Provedor de cobrança desconhecido."));
    }
}
