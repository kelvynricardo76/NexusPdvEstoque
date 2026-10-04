package com.nexus.pdv.tenant.application;

import com.nexus.pdv.subscription.domain.BillingCycle;

/**
 * Dados para criar uma empresa cliente com seu primeiro administrador.
 *
 * @param adminPassword opcional; se ausente, o administrador recebe link de definição de senha
 * @param trialDays     0 = inicia com assinatura ativa
 */
public record ProvisionTenantCommand(
        String name,
        String tradeName,
        String document,
        String email,
        String phone,
        String planCode,
        BillingCycle billingCycle,
        int trialDays,
        String adminName,
        String adminEmail,
        String adminPhone,
        String adminPassword) {
}
