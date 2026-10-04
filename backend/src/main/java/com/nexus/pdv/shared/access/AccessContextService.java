package com.nexus.pdv.shared.access;

import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.entitlement.Entitlements;
import com.nexus.pdv.permission.domain.EffectivePermissions;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.infrastructure.LimitDefinitionRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.subscription.infrastructure.TenantSubscriptionRepository;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Calcula o {@link AccessContext} do usuário autenticado a partir do banco (não da sessão),
 * de modo que desativações, mudanças de cargo, plano ou suspensão tenham efeito imediato.
 * O resultado é memorizado por requisição.
 */
@Service
public class AccessContextService {

    private static final String REQUEST_KEY = AccessContextService.class.getName() + ".CONTEXT";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final LimitDefinitionRepository limitDefinitionRepository;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public AccessContextService(UserRepository userRepository, TenantRepository tenantRepository,
            TenantSubscriptionRepository subscriptionRepository, LimitDefinitionRepository limitDefinitionRepository,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.limitDefinitionRepository = limitDefinitionRepository;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.clock = clock;
    }

    public AccessContext current() {
        AuthenticatedUser principal = CurrentUser.require();
        if (principal.isPlatformAdmin() || principal.tenantId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getAttribute(REQUEST_KEY, RequestAttributes.SCOPE_REQUEST) instanceof AccessContext cached) {
            return cached;
        }
        AccessContext context = readOnlyTx.execute(status -> load(principal));
        if (attributes != null) {
            attributes.setAttribute(REQUEST_KEY, context, RequestAttributes.SCOPE_REQUEST);
        }
        return context;
    }

    /** Descarta o contexto memorizado (após alterar os próprios direitos na mesma requisição). */
    public void evict() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(REQUEST_KEY, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private AccessContext load(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .filter(found -> found.getTenantId().equals(principal.tenantId()))
                .orElse(null);
        if (user == null || !user.isActive()) {
            revokeSession();
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Sua sessão foi encerrada. Entre novamente.");
        }
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenant.getId()).orElse(null);
        Entitlements entitlements = EntitlementService.compute(tenant, subscription, limitDefinitionRepository.findAll());
        Set<Permission> permissions = EffectivePermissions.resolve(user.getRole(), user.getPermissionOverrides(),
                entitlements.features());
        LocalDate today = LocalDate.now(clock);
        return new AccessContext(
                principal,
                tenant,
                subscription,
                EffectiveTenantStatus.AccessState.of(tenant, subscription, today),
                EffectiveTenantStatus.of(tenant, subscription),
                entitlements,
                permissions,
                user.getRole().getId(),
                user.getRole().getName(),
                user.getRole().getCode());
    }

    private static void revokeSession() {
        SecurityContextHolder.clearContext();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            HttpSession session = servlet.getRequest().getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
    }
}
