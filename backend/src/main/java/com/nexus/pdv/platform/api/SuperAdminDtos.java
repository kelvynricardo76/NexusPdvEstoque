package com.nexus.pdv.platform.api;

import com.nexus.pdv.billing.domain.InvoiceStatus;
import com.nexus.pdv.entitlement.EntitlementBreakdown;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** DTOs do painel Super Admin. Valores de limite: -1 = ilimitado. */
public final class SuperAdminDtos {

    private SuperAdminDtos() {
    }

    // ---------- Tenants ----------

    public record CreateTenantRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 150) String tradeName,
            @Size(max = 20) String document,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(max = 40) String planCode,
            BillingCycle billingCycle,
            @Min(0) @Max(365) Integer trialDays,
            @NotNull @Valid AdminRequest admin) {
    }

    public record AdminRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 128) String password) {
    }

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 150) String tradeName,
            @Size(max = 20) String document,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone) {
    }

    public enum TenantStatusAction { SUSPEND, REACTIVATE, CANCEL }

    public record TenantStatusRequest(@NotNull TenantStatusAction action, @Size(max = 300) String reason) {
    }

    public record ChangePlanRequest(@NotBlank String planCode, BillingCycle billingCycle) {
    }

    public record UpdateSubscriptionRequest(
            @NotNull SubscriptionStatus status,
            LocalDate trialEndDate,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            boolean cancelAtPeriodEnd) {
    }

    /** {@code enabled = null} remove o override (volta ao plano). */
    public record FeatureOverrideRequest(@NotBlank String featureCode, Boolean enabled) {
    }

    /** {@code value = null} remove o override; -1 = ilimitado. */
    public record LimitOverrideRequest(@NotBlank String limitCode, @Min(-1) Long value) {
    }

    public record TenantSummary(
            UUID id,
            String name,
            String tradeName,
            String document,
            String planCode,
            String planName,
            String status,
            String subscriptionStatus,
            long activeUsers,
            Instant createdAt) {
    }

    public record TenantDetail(
            UUID id,
            String name,
            String tradeName,
            String document,
            String email,
            String phone,
            String administrativeStatus,
            String statusReason,
            String status,
            boolean operable,
            Instant createdAt,
            SubscriptionView subscription,
            EntitlementBreakdown entitlements,
            TenantUsage usage) {
    }

    public record TenantUsage(long activeUsers, long products, long salesThisMonth) {
    }

    public record SubscriptionView(
            UUID id,
            UUID tenantId,
            String tenantName,
            String planCode,
            String planName,
            String status,
            String billingCycle,
            LocalDate startDate,
            LocalDate trialEndDate,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            String billingProvider,
            BigDecimal monthlyRecurringRevenue) {
    }

    public record TenantUserView(UUID id, String name, String email, String role, String status, Instant lastLoginAt) {
    }

    // ---------- Planos / catálogo ----------

    public record PlanRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "Use letras maiúsculas, números e _") String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal monthlyPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal annualPrice,
            boolean active,
            @Min(0) int displayOrder,
            @NotNull Set<String> features,
            @NotNull Map<String, Long> limits) {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            boolean active,
            int displayOrder,
            List<String> features,
            Map<String, Long> limits,
            long tenantCount) {
    }

    public record FeatureResponse(String code, String name, String description, int displayOrder, List<String> plans) {
    }

    public record FeatureUpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 300) String description) {
    }

    public record LimitResponse(String code, String name, String description, String unit, long defaultValue,
            int displayOrder) {
    }

    public record LimitUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String description,
            @Min(-1) long defaultValue) {
    }

    // ---------- Billing ----------

    public record IssueInvoiceRequest(
            @NotNull UUID tenantId,
            @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            LocalDate dueDate,
            @Size(max = 200) String description) {
    }

    public record InvoiceResponse(
            UUID id,
            UUID tenantId,
            String tenantName,
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            InvoiceStatus status,
            Instant paidAt,
            String billingProvider,
            Instant createdAt) {
    }

    // ---------- Administradores ----------

    public record CreatePlatformAdminRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record PlatformAdminStatusRequest(boolean active) {
    }

    public record PlatformAdminResponse(UUID id, String name, String email, String status, Instant lastLoginAt,
            Instant createdAt) {
    }

    // ---------- Dashboard / configurações ----------

    public record DashboardResponse(
            long totalTenants,
            long activeTenants,
            long trialTenants,
            long pastDueTenants,
            long suspendedTenants,
            long canceledTenants,
            long newTenantsLast30Days,
            BigDecimal monthlyRecurringRevenue,
            List<PlanDistribution> planDistribution,
            List<DailyCount> newTenantsByDay) {
    }

    public record PlanDistribution(String planCode, String planName, long tenants) {
    }

    public record DailyCount(LocalDate date, long count) {
    }

    public record PlatformSettingsRequest(@Min(0) @Max(365) int defaultTrialDays, @Min(0) @Max(90) int pastDueGraceDays) {
    }

    public record PlatformSettingsResponse(int defaultTrialDays, int pastDueGraceDays) {
    }
}
