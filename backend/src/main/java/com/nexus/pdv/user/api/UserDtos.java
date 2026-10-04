package com.nexus.pdv.user.api;

import com.nexus.pdv.permission.domain.PermissionEffect;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /** {@code password} opcional: sem senha, o funcionário recebe um link para definir a própria. */
    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @NotNull UUID roleId,
            @Size(max = 128) String password) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 30) String phone,
            @NotNull UUID roleId) {
    }

    public record UserStatusRequest(boolean active) {
    }

    /** Overrides completos do usuário (substitui os anteriores). */
    public record PermissionOverridesRequest(@NotNull Map<String, PermissionEffect> overrides) {
    }

    public record UserResponse(
            UUID id,
            String name,
            String email,
            String phone,
            UUID roleId,
            String roleName,
            String roleCode,
            String status,
            Instant lastLoginAt,
            Instant createdAt) {
    }

    public record UserDetailResponse(
            UserResponse user,
            Map<String, PermissionEffect> overrides,
            List<String> effectivePermissions) {
    }

    public record RoleRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotNull Set<String> permissions) {
    }

    public record RoleResponse(
            UUID id,
            String code,
            String name,
            String description,
            boolean systemRole,
            List<String> permissions,
            long userCount) {
    }

    public record PermissionResponse(
            String code,
            String module,
            String moduleLabel,
            String action,
            String label,
            String feature,
            boolean available) {
    }
}
