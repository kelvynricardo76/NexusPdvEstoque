package com.nexus.pdv.platform.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.platform.api.SuperAdminDtos.FeatureResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.FeatureUpdateRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.LimitResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.LimitUpdateRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlanRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlanResponse;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.FeatureDefinition;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.LimitDefinition;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.FeatureDefinitionRepository;
import com.nexus.pdv.plan.infrastructure.LimitDefinitionRepository;
import com.nexus.pdv.plan.infrastructure.PlanRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de planos e catálogo pelo Super Admin. Alterações que reduzem direitos de planos em uso
 * (remover feature, reduzir limite) exigem confirmação explícita — nada é removido
 * silenciosamente de contratos existentes.
 */
@Service
public class PlanAdministrationService {

    private final PlanRepository planRepository;
    private final FeatureDefinitionRepository featureRepository;
    private final LimitDefinitionRepository limitRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final AuditService auditService;

    public PlanAdministrationService(PlanRepository planRepository, FeatureDefinitionRepository featureRepository,
            LimitDefinitionRepository limitRepository, TenantSubscriptionRepository subscriptionRepository,
            AuditService auditService) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.limitRepository = limitRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return planRepository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um plano com este código.");
        }
        Plan plan = new Plan(request.code(), Texts.clean(request.name()), Texts.clean(request.description()),
                request.monthlyPrice(), request.annualPrice(), request.active(), request.displayOrder());
        plan.replaceFeatures(parseFeatures(request.features()));
        plan.replaceLimits(parseLimits(request.limits()));
        plan = planRepository.save(plan);
        auditService.record(AuditAction.CREATE, "Plan", plan.getId(), Map.of("code", plan.getCode()));
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse update(UUID id, PlanRequest request, boolean confirmImpact) {
        Plan plan = find(id);
        if (!plan.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O código do plano não pode ser alterado.");
        }
        Set<FeatureCode> newFeatures = parseFeatures(request.features());
        Map<LimitCode, Long> newLimits = parseLimits(request.limits());

        Set<FeatureCode> removed = EnumSet.noneOf(FeatureCode.class);
        removed.addAll(plan.getFeatures());
        removed.removeAll(newFeatures);
        List<String> reducedLimits = new ArrayList<>();
        plan.getLimits().forEach((code, oldValue) -> {
            Long newValue = newLimits.get(code);
            if (isReduction(oldValue, newValue)) {
                reducedLimits.add(code.name());
            }
        });
        long tenants = subscriptionRepository.countByPlanId(plan.getId());
        if (tenants > 0 && (!removed.isEmpty() || !reducedLimits.isEmpty()) && !confirmImpact) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED,
                    "Esta alteração reduz direitos de " + tenants + " empresa(s) neste plano"
                            + (removed.isEmpty() ? "" : " (features removidas: " + removed + ")")
                            + (reducedLimits.isEmpty() ? "" : " (limites reduzidos: " + reducedLimits + ")")
                            + ". Confirme para aplicar.");
        }

        plan.update(Texts.clean(request.name()), Texts.clean(request.description()), request.monthlyPrice(),
                request.annualPrice(), request.active(), request.displayOrder());
        plan.replaceFeatures(newFeatures);
        plan.replaceLimits(newLimits);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", plan.getCode());
        meta.put("affectedTenants", tenants);
        meta.put("removedFeatures", removed.stream().map(Enum::name).toList());
        meta.put("reducedLimits", reducedLimits);
        auditService.record(AuditAction.PLAN_CHANGE, "Plan", plan.getId(), meta);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<FeatureResponse> features() {
        List<Plan> plans = planRepository.findAllByOrderByDisplayOrderAscNameAsc();
        return featureRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(feature -> new FeatureResponse(feature.getCode().name(), feature.getName(),
                        feature.getDescription(), feature.getDisplayOrder(),
                        plans.stream().filter(plan -> plan.getFeatures().contains(feature.getCode()))
                                .map(Plan::getCode).toList()))
                .toList();
    }

    @Transactional
    public void updateFeature(String code, FeatureUpdateRequest request) {
        FeatureDefinition feature = featureRepository.findById(TenantAdministrationService.parse(FeatureCode.class, code, "Funcionalidade"))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        feature.update(Texts.clean(request.name()), Texts.clean(request.description()));
        auditService.record(AuditAction.UPDATE, "Feature", code, Map.of());
    }

    @Transactional(readOnly = true)
    public List<LimitResponse> limits() {
        return limitRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(limit -> new LimitResponse(limit.getCode().name(), limit.getName(), limit.getDescription(),
                        limit.getUnit(), limit.getDefaultValue(), limit.getDisplayOrder()))
                .toList();
    }

    @Transactional
    public void updateLimit(String code, LimitUpdateRequest request) {
        LimitDefinition limit = limitRepository.findById(TenantAdministrationService.parse(LimitCode.class, code, "Limite"))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        limit.update(Texts.clean(request.name()), Texts.clean(request.description()), request.defaultValue());
        auditService.record(AuditAction.UPDATE, "LimitDefinition", code, Map.of("defaultValue", request.defaultValue()));
    }

    private PlanResponse toResponse(Plan plan) {
        Map<String, Long> limits = new LinkedHashMap<>();
        plan.getLimits().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::ordinal)))
                .forEach(entry -> limits.put(entry.getKey().name(), entry.getValue()));
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                plan.getMonthlyPrice(), plan.getAnnualPrice(), plan.isActive(), plan.getDisplayOrder(),
                plan.getFeatures().stream().sorted(Comparator.comparing(Enum::ordinal)).map(Enum::name).toList(),
                limits, subscriptionRepository.countByPlanId(plan.getId()));
    }

    private Plan find(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static Set<FeatureCode> parseFeatures(Set<String> codes) {
        Set<FeatureCode> result = EnumSet.noneOf(FeatureCode.class);
        for (String code : codes) {
            result.add(TenantAdministrationService.parse(FeatureCode.class, code, "Funcionalidade"));
        }
        return result;
    }

    private static Map<LimitCode, Long> parseLimits(Map<String, Long> values) {
        Map<LimitCode, Long> result = new EnumMap<>(LimitCode.class);
        values.forEach((code, value) -> {
            if (value == null || value < LimitCode.UNLIMITED) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Valor inválido para o limite " + code + ".");
            }
            result.put(TenantAdministrationService.parse(LimitCode.class, code, "Limite"), value);
        });
        return result;
    }

    /** Redução = novo valor finito menor que o anterior, ou limite removido/virou finito a partir de ilimitado. */
    private static boolean isReduction(Long oldValue, Long newValue) {
        if (newValue == null) {
            return true;
        }
        if (LimitCode.isUnlimited(newValue)) {
            return false;
        }
        return LimitCode.isUnlimited(oldValue) || newValue < oldValue;
    }
}
