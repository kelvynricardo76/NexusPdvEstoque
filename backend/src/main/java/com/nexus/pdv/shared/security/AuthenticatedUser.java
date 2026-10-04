package com.nexus.pdv.shared.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Principal armazenado na sessão. Contém apenas identidade — permissões, features e status
 * são recalculados a cada requisição para refletir alterações imediatamente.
 *
 * @param tenantId {@code null} para administradores da plataforma (SUPER_ADMIN)
 */
public record AuthenticatedUser(UUID id, UUID tenantId, String email, String name, PrincipalType type)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isPlatformAdmin() {
        return type == PrincipalType.PLATFORM_ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + type.name()));
    }
}
