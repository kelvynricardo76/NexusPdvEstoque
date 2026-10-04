package com.nexus.pdv.billing.application;

import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.domain.Tenant;
import java.util.Optional;

/**
 * Abstração do gateway de cobrança (ex.: ASAAS, Mercado Pago). O domínio de assinatura não
 * conhece o gateway: fala apenas com esta interface.
 */
public interface BillingProvider {

    /** Identificador do provedor (gravado nas assinaturas e faturas). */
    String code();

    /** Cadastra cliente/assinatura no provedor, se aplicável. */
    void registerSubscription(Tenant tenant, TenantSubscription subscription);

    /** Emite a cobrança no provedor e devolve a referência externa, se houver. */
    Optional<String> issueInvoice(Tenant tenant, SubscriptionInvoice invoice);

    /**
     * Interpreta e valida (assinatura/HMAC) um webhook do provedor.
     *
     * @return evento normalizado; vazio se o provedor não suporta webhooks
     */
    Optional<BillingWebhookEvent> parseWebhook(String payload, java.util.Map<String, String> headers);

    /** Evento de cobrança normalizado, independente do provedor. */
    record BillingWebhookEvent(String eventId, String type, String externalInvoiceReference) {

        public static final String INVOICE_PAID = "INVOICE_PAID";
        public static final String INVOICE_OVERDUE = "INVOICE_OVERDUE";
    }
}
