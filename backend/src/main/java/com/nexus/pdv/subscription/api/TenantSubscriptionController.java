package com.nexus.pdv.subscription.api;

import com.nexus.pdv.plan.domain.FeatureDefinition;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.FeatureDefinitionRepository;
import com.nexus.pdv.plan.infrastructure.PlanRepository;
import com.nexus.pdv.platform.application.TenantUsageService;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.access.RequiresTenantAdmin;
import com.nexus.pdv.shared.access.TenantAuthenticated;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visão comercial para o tenant: assinatura atual, consumo e catálogo de planos (para a UX de
 * upgrade). Dados comerciais ficam restritos ao TENANT_ADMIN.
 */
@Tag(name = "Assinatura do tenant")
@RestController
@RequestMapping("/api")
public class TenantSubscriptionController {

    private final AccessContextService accessContextService;
    private final PlanRepository planRepository;
    private final FeatureDefinitionRepository featureRepository;
    private final TenantUsageService usageService;

    public TenantSubscriptionController(AccessContextService accessContextService, PlanRepository planRepository,
            FeatureDefinitionRepository featureRepository, TenantUsageService usageService) {
        this.accessContextService = accessContextService;
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.usageService = usageService;
    }

    @GetMapping("/subscription")
    @RequiresTenantAdmin
    public SubscriptionResponse subscription() {
        AccessContext context = accessContextService.current();
        TenantSubscription subscription = context.subscription();
        var usage = usageService.usageOf(context.tenantId());
        Map<String, Long> limits = new LinkedHashMap<>();
        context.entitlements().limits().forEach((code, value) -> limits.put(code.name(), value));
        return new SubscriptionResponse(
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus().name(),
                subscription.getBillingCycle().name(),
                subscription.getTrialEndDate(),
                subscription.getCurrentPeriodEnd(),
                context.entitlements().features().stream().map(Enum::name).sorted().toList(),
                limits,
                new Usage(usage.activeUsers(), usage.products(), usage.salesThisMonth(),
                        context.entitlements().limit(LimitCode.MAX_USERS),
                        context.entitlements().limit(LimitCode.MAX_PRODUCTS),
                        context.entitlements().limit(LimitCode.MAX_MONTHLY_SALES)));
    }

    /** Planos ativos e suas features — usado para indicar "Disponível no plano X". */
    @GetMapping("/plans/catalog")
    @TenantAuthenticated
    @Transactional(readOnly = true)
    public List<CatalogPlan> catalog() {
        Map<String, FeatureDefinition> features = new LinkedHashMap<>();
        featureRepository.findAllByOrderByDisplayOrderAsc().forEach(f -> features.put(f.getCode().name(), f));
        boolean admin = accessContextService.current().tenantAdmin();
        return planRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(plan -> toCatalog(plan, features, admin))
                .toList();
    }

    private static CatalogPlan toCatalog(Plan plan, Map<String, FeatureDefinition> definitions, boolean showPrices) {
        List<CatalogFeature> features = plan.getFeatures().stream()
                .sorted(Comparator.comparing(Enum::ordinal))
                .map(code -> {
                    FeatureDefinition definition = definitions.get(code.name());
                    return new CatalogFeature(code.name(), definition == null ? code.name() : definition.getName());
                })
                .toList();
        return new CatalogPlan(plan.getCode(), plan.getName(), plan.getDescription(),
                showPrices ? plan.getMonthlyPrice() : null, showPrices ? plan.getAnnualPrice() : null,
                plan.getDisplayOrder(), features);
    }

    public record SubscriptionResponse(String planCode, String planName, String status, String billingCycle,
            LocalDate trialEndDate, LocalDate currentPeriodEnd, List<String> features, Map<String, Long> limits,
            Usage usage) {
    }

    public record Usage(long activeUsers, long products, long salesThisMonth, long maxUsers, long maxProducts,
            long maxMonthlySales) {
    }

    public record CatalogPlan(String code, String name, String description, BigDecimal monthlyPrice,
            BigDecimal annualPrice, int displayOrder, List<CatalogFeature> features) {
    }

    public record CatalogFeature(String code, String name) {
    }
}
