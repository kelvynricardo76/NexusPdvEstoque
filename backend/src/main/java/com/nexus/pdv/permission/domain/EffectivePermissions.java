package com.nexus.pdv.permission.domain;

import com.nexus.pdv.plan.domain.FeatureCode;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Regra única de permissão efetiva:
 * <pre>
 *   (permissões do cargo ∪ overrides ALLOW) − overrides DENY
 *   ∩ permissões cuja feature o tenant possui
 * </pre>
 * Overrides não se aplicam ao TENANT_ADMIN.
 */
public final class EffectivePermissions {

    private EffectivePermissions() {
    }

    public static Set<Permission> resolve(Role role, Map<Permission, PermissionEffect> overrides,
            Set<FeatureCode> tenantFeatures) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(role.getPermissions());
        if (!role.isTenantAdmin()) {
            overrides.forEach((permission, effect) -> {
                if (effect == PermissionEffect.ALLOW) {
                    result.add(permission);
                }
            });
            overrides.forEach((permission, effect) -> {
                if (effect == PermissionEffect.DENY) {
                    result.remove(permission);
                }
            });
        }
        result.removeIf(permission -> permission.feature() != null && !tenantFeatures.contains(permission.feature()));
        return Collections.unmodifiableSet(result);
    }
}
