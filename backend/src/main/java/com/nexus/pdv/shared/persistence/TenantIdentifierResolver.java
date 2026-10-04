package com.nexus.pdv.shared.persistence;

import com.nexus.pdv.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve o tenant corrente para o Hibernate a partir do contexto autenticado — nunca a
 * partir de dados enviados pelo cliente.
 *
 * <ul>
 *   <li>Usuário de tenant: o tenant do próprio usuário.</li>
 *   <li>SUPER_ADMIN ou contexto de sistema: {@link #ROOT} (sem filtro).</li>
 *   <li>Sem autenticação: {@link #NONE}, que não corresponde a nenhum dado (fail-closed).</li>
 * </ul>
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID ROOT = new UUID(0L, 0L);
    public static final UUID NONE = new UUID(0L, 1L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (TenantContext.isSystem()) {
            return ROOT;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.isPlatformAdmin() ? ROOT : user.tenantId();
        }
        return NONE;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT.equals(tenantId);
    }
}
