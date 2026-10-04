package com.nexus.pdv.billing.application;

import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.domain.Tenant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Cobrança manual (sem gateway): faturas são registradas e baixadas pelo Super Admin.
 * Substituível por uma implementação de gateway sem alterar o domínio.
 */
@Component
public class ManualBillingProvider implements BillingProvider {

    public static final String CODE = "MANUAL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void registerSubscription(Tenant tenant, TenantSubscription subscription) {
        // Nada a registrar externamente.
    }

    @Override
    public Optional<String> issueInvoice(Tenant tenant, SubscriptionInvoice invoice) {
        return Optional.empty();
    }

    @Override
    public Optional<BillingWebhookEvent> parseWebhook(String payload, Map<String, String> headers) {
        return Optional.empty();
    }
}
