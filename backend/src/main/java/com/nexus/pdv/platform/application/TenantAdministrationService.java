package com.nexus.pdv.platform.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.platform.api.SuperAdminDtos.ChangePlanRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.SubscriptionView;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantDetail;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantStatusAction;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantSummary;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantUserView;
import com.nexus.pdv.platform.api.SuperAdminDtos.UpdateSubscriptionRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.UpdateTenantRequest;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.PlanRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.domain.TenantStatus;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.domain.UserStatus;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administração de empresas clientes pelo SUPER_ADMIN (contexto root). */
@Service
public class TenantAdministrationService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final TenantUsageService usageService;
    private final TenantSearchRepository searchRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TenantAdministrationService(TenantRepository tenantRepository,
            TenantSubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            UserRepository userRepository, EntitlementService entitlementService, TenantUsageService usageService,
            TenantSearchRepository searchRepository, AuditService auditService, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.searchRepository = searchRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TenantSummary> search(String q, EffectiveTenantStatus status, String planCode, Pageable pageable) {
        Page<Tenant> page = searchRepository.search(Texts.searchTerm(q), status, Texts.clean(planCode), pageable);
        List<UUID> ids = page.getContent().stream().map(Tenant::getId).toList();
        Map<UUID, TenantSubscription> subscriptions = ids.isEmpty() ? Map.of()
                : subscriptionRepository.findByTenantIdIn(ids).stream()
                        .collect(Collectors.toMap(TenantSubscription::getTenantId, Function.identity()));
        Map<UUID, Long> users = new HashMap<>();
        for (Object[] row : userRepository.countByTenantGrouped(UserStatus.ACTIVE)) {
            users.put((UUID) row[0], (Long) row[1]);
        }
        List<TenantSummary> content = page.getContent().stream().map(tenant -> {
            TenantSubscription subscription = subscriptions.get(tenant.getId());
            return new TenantSummary(
                    tenant.getId(),
                    tenant.getName(),
                    tenant.getTradeName(),
                    tenant.getDocument(),
                    subscription == null ? null : subscription.getPlan().getCode(),
                    subscription == null ? null : subscription.getPlan().getName(),
                    EffectiveTenantStatus.of(tenant, subscription).name(),
                    subscription == null ? null : subscription.getStatus().name(),
                    users.getOrDefault(tenant.getId(), 0L),
                    tenant.getCreatedAt());
        }).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TenantDetail detail(UUID tenantId) {
        Tenant tenant = find(tenantId);
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        return new TenantDetail(
                tenant.getId(),
                tenant.getName(),
                tenant.getTradeName(),
                tenant.getDocument(),
                tenant.getEmail(),
                tenant.getPhone(),
                tenant.getStatus().name(),
                tenant.getStatusReason(),
                EffectiveTenantStatus.of(tenant, subscription).name(),
                EffectiveTenantStatus.AccessState.of(tenant, subscription, LocalDate.now(clock))
                        == EffectiveTenantStatus.AccessState.OPERABLE,
                tenant.getCreatedAt(),
                subscription == null ? null : toView(subscription, tenant),
                entitlementService.breakdown(tenantId),
                usageService.usageOf(tenantId));
    }

    @Transactional
    public void update(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = find(tenantId);
        tenant.updateInfo(Texts.clean(request.name()), Texts.clean(request.tradeName()), Texts.digits(request.document()),
                User.normalizeEmail(Texts.clean(request.email())), Texts.clean(request.phone()));
        auditService.recordOnTenant(tenantId, AuditAction.UPDATE, "Tenant", tenantId, Map.of());
    }

    @Transactional
    public void changeStatus(UUID tenantId, TenantStatusAction action, String reason) {
        Tenant tenant = find(tenantId);
        String cleanReason = Texts.clean(reason);
        switch (action) {
            case SUSPEND -> {
                tenant.changeStatus(TenantStatus.SUSPENDED, cleanReason);
                auditService.recordOnTenant(tenantId, AuditAction.TENANT_SUSPEND, "Tenant", tenantId, reasonMeta(cleanReason));
            }
            case REACTIVATE -> {
                tenant.changeStatus(TenantStatus.ACTIVE, cleanReason);
                subscriptionRepository.findByTenantId(tenantId)
                        .filter(subscription -> subscription.getStatus() == SubscriptionStatus.CANCELED
                                || subscription.getStatus() == SubscriptionStatus.SUSPENDED)
                        .ifPresent(subscription -> subscription.renewPeriod(LocalDate.now(clock)));
                auditService.recordOnTenant(tenantId, AuditAction.TENANT_REACTIVATE, "Tenant", tenantId, reasonMeta(cleanReason));
            }
            case CANCEL -> {
                tenant.changeStatus(TenantStatus.CANCELED, cleanReason);
                subscriptionRepository.findByTenantId(tenantId)
                        .ifPresent(subscription -> subscription.changeStatus(SubscriptionStatus.CANCELED));
                auditService.recordOnTenant(tenantId, AuditAction.TENANT_CANCEL, "Tenant", tenantId, reasonMeta(cleanReason));
            }
        }
    }

    @Transactional
    public void changePlan(UUID tenantId, ChangePlanRequest request) {
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Plan plan = planRepository.findByCode(request.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Plano inválido ou inativo."));
        String previous = subscription.getPlan().getCode();
        BillingCycle cycle = request.billingCycle() != null ? request.billingCycle() : subscription.getBillingCycle();
        subscription.changePlan(plan, cycle);
        auditService.recordOnTenant(tenantId, AuditAction.PLAN_CHANGE, "TenantSubscription", subscription.getId(),
                Map.of("from", previous, "to", plan.getCode(), "cycle", cycle.name()));
    }

    @Transactional
    public void updateSubscription(UUID tenantId, UpdateSubscriptionRequest request) {
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.currentPeriodStart() != null && request.currentPeriodEnd() != null
                && request.currentPeriodEnd().isBefore(request.currentPeriodStart())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O fim do período deve ser posterior ao início.");
        }
        String previous = subscription.getStatus().name();
        subscription.updateTerms(request.status(), request.trialEndDate(), request.currentPeriodStart(),
                request.currentPeriodEnd(), request.cancelAtPeriodEnd());
        auditService.recordOnTenant(tenantId, AuditAction.SUBSCRIPTION_CHANGE, "TenantSubscription",
                subscription.getId(), Map.of("from", previous, "to", request.status().name()));
    }

    @Transactional
    public void setFeatureOverride(UUID tenantId, String featureCode, Boolean enabled) {
        FeatureCode feature = parse(FeatureCode.class, featureCode, "Funcionalidade");
        Tenant tenant = find(tenantId);
        tenant.setFeatureOverride(feature, enabled);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("feature", feature.name());
        meta.put("enabled", enabled == null ? "PLAN_DEFAULT" : enabled);
        auditService.recordOnTenant(tenantId, AuditAction.FEATURE_OVERRIDE, "Tenant", tenantId, meta);
    }

    @Transactional
    public void setLimitOverride(UUID tenantId, String limitCode, Long value) {
        LimitCode limit = parse(LimitCode.class, limitCode, "Limite");
        if (value != null && value < LimitCode.UNLIMITED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Valor de limite inválido.");
        }
        Tenant tenant = find(tenantId);
        tenant.setLimitOverride(limit, value);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("limit", limit.name());
        meta.put("value", value == null ? "PLAN_DEFAULT" : value);
        auditService.recordOnTenant(tenantId, AuditAction.LIMIT_OVERRIDE, "Tenant", tenantId, meta);
    }

    @Transactional(readOnly = true)
    public List<TenantUserView> users(UUID tenantId) {
        find(tenantId);
        return userRepository.findAllOfTenant(tenantId).stream()
                .map(user -> new TenantUserView(user.getId(), user.getName(), user.getEmail(), user.getRole().getName(),
                        user.getStatus().name(), user.getLastLoginAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionView> subscriptions(SubscriptionStatus status, String planCode, Pageable pageable) {
        Page<TenantSubscription> page = subscriptionRepository.search(status, Texts.clean(planCode), pageable);
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(page.getContent().stream()
                        .map(TenantSubscription::getTenantId).toList())
                .stream().collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return page.map(subscription -> toView(subscription, tenants.get(subscription.getTenantId())));
    }

    static SubscriptionView toView(TenantSubscription subscription, Tenant tenant) {
        return new SubscriptionView(
                subscription.getId(),
                subscription.getTenantId(),
                tenant == null ? null : tenant.displayName(),
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus().name(),
                subscription.getBillingCycle().name(),
                subscription.getStartDate(),
                subscription.getTrialEndDate(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getBillingProvider(),
                subscription.monthlyRecurringRevenue());
    }

    private Tenant find(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static Map<String, Object> reasonMeta(String reason) {
        return reason == null ? Map.of() : Map.of("reason", reason);
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " desconhecido(a): " + value);
        }
    }
}
