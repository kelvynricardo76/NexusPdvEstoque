package com.nexus.pdv.entitlement;

import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.LimitDefinition;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.LimitDefinitionRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor central de direitos do tenant. Toda regra "o cliente pode usar X?" / "quanto pode usar?"
 * passa por aqui — nenhum código compara o plano diretamente.
 *
 * <p>Precedência: override do tenant → configuração do plano → padrão do catálogo.
 */
@Service
public class EntitlementService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final LimitDefinitionRepository limitDefinitionRepository;

    public EntitlementService(TenantRepository tenantRepository, TenantSubscriptionRepository subscriptionRepository,
            LimitDefinitionRepository limitDefinitionRepository) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.limitDefinitionRepository = limitDefinitionRepository;
    }

    @Transactional(readOnly = true)
    public Entitlements resolve(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        return compute(tenant, subscription, limitDefinitionRepository.findAll());
    }

    public boolean hasFeature(UUID tenantId, FeatureCode feature) {
        return resolve(tenantId).hasFeature(feature);
    }

    public long getLimit(UUID tenantId, LimitCode limit) {
        return resolve(tenantId).limit(limit);
    }

    public boolean canCreateUser(UUID tenantId, long currentActiveUsers) {
        return resolve(tenantId).allowsOneMore(LimitCode.MAX_USERS, currentActiveUsers);
    }

    public boolean canCreateProduct(UUID tenantId, long currentProducts) {
        return resolve(tenantId).allowsOneMore(LimitCode.MAX_PRODUCTS, currentProducts);
    }

    /** Falha com PLAN_LIMIT_REACHED se adicionar {@code amount} ultrapassar o limite. */
    public void assertWithinLimit(UUID tenantId, LimitCode limit, long currentUsage, long amount) {
        long max = getLimit(tenantId, limit);
        if (!LimitCode.isUnlimited(max) && currentUsage + amount > max) {
            throw new BusinessException(ErrorCode.PLAN_LIMIT_REACHED, limitMessage(limit, max));
        }
    }

    public static String limitMessage(LimitCode limit, long max) {
        return switch (limit) {
            case MAX_USERS -> "Limite de " + max + " usuários ativos do seu plano atingido.";
            case MAX_PRODUCTS -> "Limite de " + max + " produtos do seu plano atingido.";
            case MAX_MONTHLY_SALES -> "Limite de " + max + " vendas mensais do seu plano atingido.";
            case MAX_BRANCHES -> "Limite de " + max + " filiais do seu plano atingido.";
            case REPORT_HISTORY_DAYS -> "Seu plano permite relatórios dos últimos " + max + " dias.";
            case STORAGE_LIMIT_MB -> "Limite de armazenamento de " + max + " MB atingido.";
        };
    }

    /** Detalhamento com a origem de cada direito (usado pelo Super Admin). */
    @Transactional(readOnly = true)
    public EntitlementBreakdown breakdown(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Plan plan = subscriptionRepository.findByTenantId(tenantId).map(TenantSubscription::getPlan).orElse(null);
        Set<FeatureCode> planFeatures = plan != null ? plan.getFeatures() : Set.of();
        Map<LimitCode, Long> planLimits = plan != null ? plan.getLimits() : Map.of();

        List<EntitlementBreakdown.FeatureEntry> features = new ArrayList<>();
        for (FeatureCode code : FeatureCode.values()) {
            boolean inPlan = planFeatures.contains(code);
            Boolean override = tenant.getFeatureOverrides().get(code);
            boolean effective = override != null ? override : inPlan;
            features.add(new EntitlementBreakdown.FeatureEntry(code, inPlan, override, effective));
        }
        List<EntitlementBreakdown.LimitEntry> limits = new ArrayList<>();
        for (LimitDefinition definition : limitDefinitionRepository.findAllByOrderByDisplayOrderAsc()) {
            LimitCode code = definition.getCode();
            Long planValue = planLimits.get(code);
            Long override = tenant.getLimitOverrides().get(code);
            long effective = override != null ? override : planValue != null ? planValue : definition.getDefaultValue();
            limits.add(new EntitlementBreakdown.LimitEntry(code, definition.getName(), definition.getDefaultValue(),
                    planValue, override, effective));
        }
        return new EntitlementBreakdown(features, limits);
    }

    public static Entitlements compute(Tenant tenant, TenantSubscription subscription, List<LimitDefinition> definitions) {
        Plan plan = subscription != null ? subscription.getPlan() : null;

        Set<FeatureCode> features = EnumSet.noneOf(FeatureCode.class);
        if (plan != null) {
            features.addAll(plan.getFeatures());
        }
        tenant.getFeatureOverrides().forEach((feature, enabled) -> {
            if (enabled) {
                features.add(feature);
            } else {
                features.remove(feature);
            }
        });

        Map<LimitCode, Long> limits = new EnumMap<>(LimitCode.class);
        for (LimitDefinition definition : definitions) {
            limits.put(definition.getCode(), definition.getDefaultValue());
        }
        if (plan != null) {
            limits.putAll(plan.getLimits());
        }
        limits.putAll(tenant.getLimitOverrides());

        return new Entitlements(
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                Collections.unmodifiableSet(features),
                Collections.unmodifiableMap(limits));
    }
}
