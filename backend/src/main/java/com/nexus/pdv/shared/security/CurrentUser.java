package com.nexus.pdv.shared.security;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao principal autenticado da requisição atual. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return find().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    /** Tenant do usuário autenticado. Falha para administradores da plataforma. */
    public static UUID requireTenantId() {
        AuthenticatedUser user = require();
        if (user.tenantId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return user.tenantId();
    }
}
