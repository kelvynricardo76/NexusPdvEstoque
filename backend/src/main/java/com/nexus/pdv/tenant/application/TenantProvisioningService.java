package com.nexus.pdv.tenant.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.auth.application.PasswordResetService;
import com.nexus.pdv.billing.application.BillingProvider;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.permission.domain.RoleCode;
import com.nexus.pdv.permission.infrastructure.RoleRepository;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.plan.infrastructure.PlanRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.domain.TenantSettings;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.tenant.infrastructure.TenantSettingsRepository;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação completa de um tenant: empresa, configurações, assinatura, cargos padrão,
 * administrador e contadores. Executa em contexto root (Super Admin ou sistema).
 */
@Service
public class TenantProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantCounterService counterService;
    private final PasswordResetService passwordResetService;
    private final BillingProvider billingProvider;
    private final AuditService auditService;
    private final Clock clock;

    public TenantProvisioningService(TenantRepository tenantRepository, TenantSettingsRepository settingsRepository,
            TenantSubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder,
            TenantCounterService counterService, PasswordResetService passwordResetService,
            BillingProvider billingProvider, AuditService auditService, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.counterService = counterService;
        this.passwordResetService = passwordResetService;
        this.billingProvider = billingProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Tenant provision(ProvisionTenantCommand command) {
        Plan plan = planRepository.findByCode(command.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Plano inválido ou inativo."));
        String adminEmail = User.normalizeEmail(command.adminEmail());
        if (userRepository.existsByEmail(adminEmail)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        boolean hasPassword = command.adminPassword() != null && !command.adminPassword().isBlank();
        if (hasPassword) {
            PasswordPolicy.validate(command.adminPassword(), adminEmail);
        }

        String name = Texts.clean(command.name());
        String tradeName = Texts.clean(command.tradeName());
        String document = Texts.digits(command.document());
        String email = User.normalizeEmail(Texts.clean(command.email()));
        String phone = Texts.clean(command.phone());

        Tenant tenant = tenantRepository.save(new Tenant(name, tradeName, document, email, phone));
        settingsRepository.save(new TenantSettings(tenant.getId(), name, tradeName, document, email, phone));

        LocalDate today = LocalDate.now(clock);
        LocalDate trialEnd = command.trialDays() > 0 ? today.plusDays(command.trialDays()) : null;
        BillingCycle cycle = command.billingCycle() == null ? BillingCycle.MONTHLY : command.billingCycle();
        TenantSubscription subscription = subscriptionRepository.save(
                new TenantSubscription(tenant.getId(), plan, cycle, today, trialEnd, billingProvider.code()));
        billingProvider.registerSubscription(tenant, subscription);

        Map<RoleCode, Role> roles = new EnumMap<>(RoleCode.class);
        for (RoleCode code : RoleCode.values()) {
            roles.put(code, roleRepository.save(Role.standard(tenant.getId(), code)));
        }

        String rawPassword = hasPassword ? command.adminPassword() : randomSecret();
        User admin = userRepository.save(User.provisioned(tenant.getId(), roles.get(RoleCode.TENANT_ADMIN),
                Texts.clean(command.adminName()), adminEmail, Texts.clean(command.adminPhone()),
                passwordEncoder.encode(rawPassword)));

        // O contador é gravado via JDBC: garante que o tenant já foi enviado ao banco (FK).
        tenantRepository.flush();
        counterService.initialize(tenant.getId(), TenantCounterService.SALE_NUMBER);

        if (!hasPassword) {
            passwordResetService.issueToken(admin);
        }
        auditService.recordOnTenant(tenant.getId(), AuditAction.TENANT_CREATE, "Tenant", tenant.getId(),
                Map.of("plan", plan.getCode(), "trialDays", command.trialDays()));
        return tenant;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "9a";
    }
}
