package com.nexus.pdv.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs de autenticação. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** {@code email} aceita também o usuário curto de demonstração (somente no perfil dev). */
    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    /**
     * Sessão atual do usuário de tenant. Contém tudo que o frontend precisa para montar o menu
     * dinâmico — mas o frontend NÃO é camada de segurança: o backend revalida cada requisição.
     *
     * @param limits valores efetivos; -1 = ilimitado
     */
    public record TenantMeResponse(
            String type,
            UserInfo user,
            TenantInfo tenant,
            SubscriptionInfo subscription,
            List<String> features,
            List<String> permissions,
            Map<String, Long> limits,
            boolean tenantAdmin,
            boolean operable,
            String blockReason) {
    }

    public record PlatformMeResponse(String type, UUID id, String name, String email) {
    }

    public record UserInfo(UUID id, String name, String email, RoleInfo role) {
    }

    public record RoleInfo(UUID id, String name, String code) {
    }

    public record TenantInfo(UUID id, String name, String tradeName, String status, String logoDataUrl,
            String primaryColor, String timezone, String currency) {
    }

    public record SubscriptionInfo(String planCode, String planName, String status, LocalDate trialEndDate,
            LocalDate currentPeriodEnd) {
    }
}
