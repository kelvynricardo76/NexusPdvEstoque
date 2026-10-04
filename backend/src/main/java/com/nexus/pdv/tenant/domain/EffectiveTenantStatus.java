package com.nexus.pdv.tenant.domain;

import com.nexus.pdv.subscription.domain.TenantSubscription;
import java.time.LocalDate;

/**
 * Situação exibida do tenant, combinando status administrativo e assinatura:
 * suspensão/cancelamento administrativo prevalece sobre a situação comercial.
 */
public enum EffectiveTenantStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELED;

    public static EffectiveTenantStatus of(Tenant tenant, TenantSubscription subscription) {
        if (tenant.getStatus() == TenantStatus.SUSPENDED) return SUSPENDED;
        if (tenant.getStatus() == TenantStatus.CANCELED) return CANCELED;
        if (subscription == null) return SUSPENDED;
        return switch (subscription.getStatus()) {
            case TRIAL -> TRIAL;
            case ACTIVE -> ACTIVE;
            case PAST_DUE -> PAST_DUE;
            case SUSPENDED -> SUSPENDED;
            case CANCELED -> CANCELED;
        };
    }

    /** Estado de acesso operacional do tenant. */
    public enum AccessState {
        OPERABLE,
        TENANT_SUSPENDED,
        SUBSCRIPTION_INACTIVE;

        public static AccessState of(Tenant tenant, TenantSubscription subscription, LocalDate today) {
            if (tenant.getStatus() != TenantStatus.ACTIVE) return TENANT_SUSPENDED;
            if (subscription == null || !subscription.allowsOperation(today)) return SUBSCRIPTION_INACTIVE;
            return OPERABLE;
        }
    }
}
