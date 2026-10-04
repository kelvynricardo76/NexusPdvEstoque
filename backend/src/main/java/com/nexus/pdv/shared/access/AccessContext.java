package com.nexus.pdv.shared.access;

import com.nexus.pdv.entitlement.Entitlements;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.RoleCode;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus.AccessState;
import com.nexus.pdv.tenant.domain.Tenant;
import java.util.Set;
import java.util.UUID;

/**
 * Direitos efetivos do usuário de tenant na requisição atual (calculado uma vez por requisição).
 */
public record AccessContext(
        AuthenticatedUser principal,
        Tenant tenant,
        TenantSubscription subscription,
        AccessState accessState,
        EffectiveTenantStatus effectiveStatus,
        Entitlements entitlements,
        Set<Permission> permissions,
        UUID roleId,
        String roleName,
        RoleCode roleCode) {

    public UUID userId() {
        return principal.id();
    }

    public UUID tenantId() {
        return principal.tenantId();
    }

    public boolean tenantAdmin() {
        return roleCode == RoleCode.TENANT_ADMIN;
    }

    public boolean operable() {
        return accessState == AccessState.OPERABLE;
    }

    public boolean hasFeature(FeatureCode feature) {
        return entitlements.hasFeature(feature);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /** Verifica feature + permissão, com o código de erro adequado para cada caso. */
    public void require(Permission permission) {
        if (permission.feature() != null && !hasFeature(permission.feature())) {
            throw new BusinessException(ErrorCode.FEATURE_NOT_AVAILABLE);
        }
        if (!hasPermission(permission)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public void requireFeature(FeatureCode feature) {
        if (!hasFeature(feature)) {
            throw new BusinessException(ErrorCode.FEATURE_NOT_AVAILABLE);
        }
    }
}
