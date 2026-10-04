package com.nexus.pdv.shared.security;

/**
 * Tipos de identidade. SUPER_ADMIN (Nexus Development) fica fora do RBAC operacional dos tenants.
 */
public enum PrincipalType {
    PLATFORM_ADMIN,
    TENANT_USER
}
