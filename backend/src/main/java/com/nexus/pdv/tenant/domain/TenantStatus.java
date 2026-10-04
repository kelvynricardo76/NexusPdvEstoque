package com.nexus.pdv.tenant.domain;

/**
 * Situação administrativa do tenant, controlada pelo Super Admin.
 * A situação comercial fica na assinatura ({@code SubscriptionStatus}); a situação efetiva
 * exibida combina as duas ({@code EffectiveTenantStatus}).
 */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    CANCELED
}
