package com.nexus.pdv.user.domain;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.PermissionEffect;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Funcionário de um tenant. O e-mail é armazenado normalizado (minúsculas) e é único globalmente. */
@Entity
@Table(name = "users")
public class User extends TenantVersionedEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission_overrides", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "permission_code", length = 50)
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 5)
    private Map<Permission, PermissionEffect> permissionOverrides = new HashMap<>();

    protected User() {
    }

    public User(Role role, String name, String email, String phone, String passwordHash) {
        this.role = role;
        this.name = name;
        this.email = normalizeEmail(email);
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
    }

    /** Criação em contexto de sistema (provisionamento pelo Super Admin). */
    public static User provisioned(UUID tenantId, Role role, String name, String email, String phone, String passwordHash) {
        User user = new User(role, name, email, phone, passwordHash);
        user.assignTenant(tenantId);
        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isTenantAdmin() {
        return role.isTenantAdmin();
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
        if (newRole.isTenantAdmin()) {
            permissionOverrides.clear();
        }
    }

    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void replaceOverrides(Map<Permission, PermissionEffect> overrides) {
        permissionOverrides.clear();
        permissionOverrides.putAll(overrides);
    }

    public void registerLogin(Instant when) {
        this.lastLoginAt = when;
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Map<Permission, PermissionEffect> getPermissionOverrides() {
        return permissionOverrides.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(permissionOverrides));
    }
}
