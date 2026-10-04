package com.nexus.pdv.platform.application;

import com.nexus.pdv.platform.api.SuperAdminDtos.DailyCount;
import com.nexus.pdv.platform.api.SuperAdminDtos.DashboardResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlanDistribution;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.PlanRepository;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicadores da plataforma para o Super Admin. */
@Service
public class PlatformDashboardService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final Clock clock;

    public PlatformDashboardService(TenantRepository tenantRepository,
            TenantSubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<UUID, TenantSubscription> subscriptions = subscriptionRepository.findAll().stream()
                .collect(Collectors.toMap(TenantSubscription::getTenantId, Function.identity()));

        Map<EffectiveTenantStatus, Long> byStatus = new EnumMap<>(EffectiveTenantStatus.class);
        BigDecimal mrr = BigDecimal.ZERO;
        for (Tenant tenant : tenants) {
            TenantSubscription subscription = subscriptions.get(tenant.getId());
            EffectiveTenantStatus status = EffectiveTenantStatus.of(tenant, subscription);
            byStatus.merge(status, 1L, Long::sum);
            if (subscription != null && (status == EffectiveTenantStatus.ACTIVE || status == EffectiveTenantStatus.PAST_DUE)) {
                mrr = mrr.add(subscription.monthlyRecurringRevenue());
            }
        }

        List<PlanDistribution> distribution = new ArrayList<>();
        for (Plan plan : planRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            long count = subscriptions.values().stream().filter(s -> s.getPlan().getId().equals(plan.getId())).count();
            distribution.add(new PlanDistribution(plan.getCode(), plan.getName(), count));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(29);
        Map<LocalDate, Long> perDay = tenants.stream()
                .map(tenant -> LocalDate.ofInstant(tenant.getCreatedAt(), ZoneOffset.UTC))
                .filter(date -> !date.isBefore(start))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<DailyCount> newByDay = new ArrayList<>();
        long newLast30 = 0;
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            long count = perDay.getOrDefault(date, 0L);
            newLast30 += count;
            newByDay.add(new DailyCount(date, count));
        }

        return new DashboardResponse(
                tenants.size(),
                byStatus.getOrDefault(EffectiveTenantStatus.ACTIVE, 0L),
                byStatus.getOrDefault(EffectiveTenantStatus.TRIAL, 0L),
                byStatus.getOrDefault(EffectiveTenantStatus.PAST_DUE, 0L),
                byStatus.getOrDefault(EffectiveTenantStatus.SUSPENDED, 0L),
                byStatus.getOrDefault(EffectiveTenantStatus.CANCELED, 0L),
                newLast30,
                mrr,
                distribution,
                newByDay);
    }
}
