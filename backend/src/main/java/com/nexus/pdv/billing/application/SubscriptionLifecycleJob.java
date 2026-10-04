package com.nexus.pdv.billing.application;

import com.nexus.pdv.billing.domain.InvoiceStatus;
import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import com.nexus.pdv.billing.infrastructure.SubscriptionInvoiceRepository;
import com.nexus.pdv.platform.application.PlatformSettingsService;
import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rotina diária do ciclo de vida comercial:
 * fatura vencida → OVERDUE; assinatura ativa com fatura vencida → PAST_DUE;
 * PAST_DUE além da carência → SUSPENDED (tenant deixa de operar).
 */
@Component
public class SubscriptionLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);

    private final SubscriptionInvoiceRepository invoiceRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlatformSettingsService platformSettings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SubscriptionLifecycleJob(SubscriptionInvoiceRepository invoiceRepository,
            TenantSubscriptionRepository subscriptionRepository, PlatformSettingsService platformSettings,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.platformSettings = platformSettings;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.billing.lifecycle-cron:0 15 3 * * *}")
    public void run() {
        TenantContext.runAsSystem(() -> tx.executeWithoutResult(status -> process(LocalDate.now(clock))));
    }

    void process(LocalDate today) {
        int graceDays = platformSettings.pastDueGraceDays();
        int changed = 0;
        for (SubscriptionInvoice invoice : invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.PENDING, today)) {
            invoice.markOverdue();
            changed++;
        }
        for (SubscriptionInvoice invoice : invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.OVERDUE, today)) {
            var subscription = subscriptionRepository.findById(invoice.getSubscriptionId()).orElse(null);
            if (subscription == null) {
                continue;
            }
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                subscription.changeStatus(SubscriptionStatus.PAST_DUE);
                changed++;
            } else if (subscription.getStatus() == SubscriptionStatus.PAST_DUE
                    && invoice.getDueDate().plusDays(graceDays).isBefore(today)) {
                subscription.changeStatus(SubscriptionStatus.SUSPENDED);
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Ciclo de vida de assinaturas: {} alterações aplicadas.", changed);
        }
    }
}
