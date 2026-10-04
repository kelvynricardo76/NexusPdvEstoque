package com.nexus.pdv.auth.application;

import com.nexus.pdv.auth.api.AuthDtos.RoleInfo;
import com.nexus.pdv.auth.api.AuthDtos.SubscriptionInfo;
import com.nexus.pdv.auth.api.AuthDtos.TenantInfo;
import com.nexus.pdv.auth.api.AuthDtos.TenantMeResponse;
import com.nexus.pdv.auth.api.AuthDtos.UserInfo;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus.AccessState;
import com.nexus.pdv.tenant.domain.TenantSettings;
import com.nexus.pdv.tenant.infrastructure.TenantSettingsRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a visão da sessão atual do usuário de tenant (menu dinâmico, bloqueios, limites). */
@Service
public class MeService {

    private final AccessContextService accessContextService;
    private final TenantSettingsRepository settingsRepository;

    public MeService(AccessContextService accessContextService, TenantSettingsRepository settingsRepository) {
        this.accessContextService = accessContextService;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public TenantMeResponse tenantMe() {
        AccessContext context = accessContextService.current();
        AuthenticatedUser principal = context.principal();
        TenantSettings settings = settingsRepository.findById(context.tenantId()).orElse(null);
        TenantSubscription subscription = context.subscription();

        Map<String, Long> limits = new LinkedHashMap<>();
        context.entitlements().limits().forEach((code, value) -> limits.put(code.name(), value));

        return new TenantMeResponse(
                principal.type().name(),
                new UserInfo(principal.id(), principal.name(), principal.email(),
                        new RoleInfo(context.roleId(), context.roleName(),
                                context.roleCode() == null ? null : context.roleCode().name())),
                new TenantInfo(
                        context.tenantId(),
                        context.tenant().getName(),
                        settings != null && settings.getTradeName() != null ? settings.getTradeName() : context.tenant().displayName(),
                        context.effectiveStatus().name(),
                        settings == null ? null : settings.getLogoDataUrl(),
                        settings == null ? null : settings.getPrimaryColor(),
                        settings == null ? TenantSettings.DEFAULT_TIMEZONE : settings.getTimezone(),
                        settings == null ? TenantSettings.DEFAULT_CURRENCY : settings.getCurrency()),
                subscription == null ? null : new SubscriptionInfo(
                        subscription.getPlan().getCode(),
                        subscription.getPlan().getName(),
                        subscription.getStatus().name(),
                        subscription.getTrialEndDate(),
                        subscription.getCurrentPeriodEnd()),
                context.entitlements().features().stream().map(Enum::name).sorted().toList(),
                context.permissions().stream().sorted(Comparator.comparing(Enum::ordinal)).map(Enum::name).toList(),
                limits,
                context.tenantAdmin(),
                context.operable(),
                context.accessState() == AccessState.OPERABLE ? null : context.accessState().name());
    }
}
