package com.nexus.pdv.platform.domain;

import com.nexus.pdv.shared.persistence.VersionedEntity;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** Administrador da plataforma (SUPER_ADMIN) — pertence à Nexus Development, não a um tenant. */
@Entity
@Table(name = "platform_admins")
public class PlatformAdmin extends VersionedEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected PlatformAdmin() {
    }

    public PlatformAdmin(String name, String email, String passwordHash) {
        this.name = name;
        this.email = User.normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void update(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void registerLogin(Instant when) {
        this.lastLoginAt = when;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
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
}
