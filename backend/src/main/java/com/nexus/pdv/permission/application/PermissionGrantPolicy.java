package com.nexus.pdv.permission.application;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.user.domain.User;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Regras anti-escalonamento de privilégio dentro do tenant:
 * <ul>
 *   <li>Só TENANT_ADMIN concede o cargo Administrador ou altera administradores.</li>
 *   <li>Quem não é TENANT_ADMIN só concede permissões que ele próprio possui.</li>
 *   <li>Não é possível conceder permissões de módulos não contratados.</li>
 *   <li>Ninguém altera o próprio cargo, status ou permissões.</li>
 * </ul>
 */
public final class PermissionGrantPolicy {

    private PermissionGrantPolicy() {
    }

    public static void assertCanAssignRole(AccessContext grantor, Role role) {
        if (role.isTenantAdmin() && !grantor.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Somente administradores podem conceder o cargo Administrador.");
        }
        assertHolds(grantor, role.getPermissions());
    }

    public static void assertCanManage(AccessContext grantor, User target) {
        if (grantor.userId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não pode alterar o próprio acesso.");
        }
        if (target.isTenantAdmin() && !grantor.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Somente administradores podem alterar outro administrador.");
        }
    }

    /**
     * Valida permissões recém-concedidas (as já existentes antes da alteração não são revalidadas,
     * para não bloquear edições após um downgrade de plano).
     */
    public static void assertCanGrant(AccessContext grantor, Collection<Permission> requested, Collection<Permission> previous) {
        Set<Permission> added = EnumSet.noneOf(Permission.class);
        added.addAll(requested);
        added.removeAll(previous);
        for (Permission permission : added) {
            if (permission.feature() != null && !grantor.hasFeature(permission.feature())) {
                throw new BusinessException(ErrorCode.FEATURE_NOT_AVAILABLE,
                        "A permissão \"" + permission.label() + "\" pertence a um módulo não contratado.");
            }
        }
        assertHolds(grantor, added);
    }

    private static void assertHolds(AccessContext grantor, Collection<Permission> permissions) {
        if (grantor.tenantAdmin()) {
            return;
        }
        for (Permission permission : permissions) {
            if (!grantor.hasPermission(permission)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não pode conceder permissões que não possui.");
            }
        }
    }
}
