package com.nexus.pdv.permission.domain;

/**
 * Efeito de um override individual de permissão.
 * ALLOW concede além do cargo; DENY remove mesmo que o cargo conceda. DENY sempre vence.
 */
public enum PermissionEffect {
    ALLOW,
    DENY
}
