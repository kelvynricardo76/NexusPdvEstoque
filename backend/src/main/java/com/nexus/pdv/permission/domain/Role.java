package com.nexus.pdv.permission.domain;

import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Cargo: conjunto reutilizável de permissões dentro de um tenant. */
@Entity
@Table(name = "roles")
public class Role extends TenantVersionedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 40, updatable = false)
    private RoleCode code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false, length = 50)
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    /** Cargo personalizado criado pelo administrador do tenant. */
    public static Role custom(String name, String description, Set<Permission> permissions) {
        Role role = new Role();
        role.name = name;
        role.description = description;
        role.permissions.addAll(permissions);
        return role;
    }

    /** Cargo padrão criado no provisionamento do tenant (contexto de sistema). */
    public static Role standard(UUID tenantId, RoleCode code) {
        Role role = new Role();
        role.assignTenant(tenantId);
        role.code = code;
        role.name = code.defaultName();
        role.description = code.defaultDescription();
        role.systemRole = code == RoleCode.TENANT_ADMIN;
        role.permissions.addAll(code.defaultPermissions());
        return role;
    }

    public boolean isTenantAdmin() {
        return code == RoleCode.TENANT_ADMIN;
    }

    public void update(String name, String description, Set<Permission> newPermissions) {
        this.name = name;
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    /** Permissões concedidas pelo cargo (TENANT_ADMIN sempre possui o catálogo completo). */
    public Set<Permission> getPermissions() {
        if (isTenantAdmin()) {
            return EnumSet.allOf(Permission.class);
        }
        return permissions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }
}
