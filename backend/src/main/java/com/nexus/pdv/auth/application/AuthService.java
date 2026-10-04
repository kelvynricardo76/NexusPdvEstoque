package com.nexus.pdv.auth.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.audit.domain.AuditLog.ActorType;
import com.nexus.pdv.platform.domain.PlatformAdmin;
import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.security.LoginAttemptService;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.shared.security.PrincipalType;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Autenticação de usuários de tenant e administradores da plataforma.
 * Mensagens de erro genéricas (não revelam se o e-mail existe) e tempo de resposta equalizado.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;
    private final String devLoginUsername;
    private final String devLoginEmail;

    /**
     * {@code nexus.dev-login.*} só é definido no perfil dev: permite entrar com um nome de usuário
     * curto (ex.: "admin") mapeado para o e-mail de uma conta de demonstração. Em produção fica vazio.
     */
    public AuthService(UserRepository userRepository, PlatformAdminRepository platformAdminRepository,
            PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts, SessionService sessionService,
            AuditService auditService, PlatformTransactionManager transactionManager, Clock clock,
            @Value("${nexus.dev-login.username:}") String devLoginUsername,
            @Value("${nexus.dev-login.email:}") String devLoginEmail) {
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("nexus-timing-equalizer");
        this.devLoginUsername = User.normalizeEmail(devLoginUsername);
        this.devLoginEmail = User.normalizeEmail(devLoginEmail);
    }

    public AuthenticatedUser loginTenantUser(String email, String password, HttpServletRequest request,
            HttpServletResponse response) {
        String normalized = User.normalizeEmail(email);
        if (!devLoginUsername.isEmpty() && !devLoginEmail.isEmpty() && devLoginUsername.equals(normalized)) {
            normalized = devLoginEmail;
        }
        String ip = request.getRemoteAddr();
        loginAttempts.assertNotBlocked(normalized, ip);

        String login = normalized;
        LoginOutcome outcome = TenantContext.callAsSystem(() -> tx.execute(status -> {
            User user = userRepository.findByEmail(login).orElse(null);
            if (user == null) {
                passwordEncoder.matches(password, dummyHash);
                return LoginOutcome.failed(null, null, null);
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isActive()) {
                return LoginOutcome.failed(user.getTenantId(), user.getId(), user.getName());
            }
            user.registerLogin(clock.instant());
            return LoginOutcome.success(new AuthenticatedUser(user.getId(), user.getTenantId(), user.getEmail(),
                    user.getName(), PrincipalType.TENANT_USER));
        }));
        return complete(outcome, normalized, ip, request, response);
    }

    public AuthenticatedUser loginPlatformAdmin(String email, String password, HttpServletRequest request,
            HttpServletResponse response) {
        String normalized = User.normalizeEmail(email);
        String ip = request.getRemoteAddr();
        loginAttempts.assertNotBlocked(normalized, ip);

        LoginOutcome outcome = tx.execute(status -> {
            PlatformAdmin admin = platformAdminRepository.findByEmail(normalized).orElse(null);
            if (admin == null) {
                passwordEncoder.matches(password, dummyHash);
                return LoginOutcome.failed(null, null, null);
            }
            if (!passwordEncoder.matches(password, admin.getPasswordHash()) || !admin.isActive()) {
                return LoginOutcome.failed(null, admin.getId(), admin.getName());
            }
            admin.registerLogin(clock.instant());
            return LoginOutcome.success(new AuthenticatedUser(admin.getId(), null, admin.getEmail(), admin.getName(),
                    PrincipalType.PLATFORM_ADMIN));
        });
        return complete(outcome, normalized, ip, request, response);
    }

    public void logout(HttpServletRequest request) {
        CurrentUser.find().ifPresent(user -> auditService.record(AuditAction.LOGOUT, "Session", user.id()));
        sessionService.terminate(request);
    }

    /** Troca da própria senha (exige a senha atual). */
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AuthenticatedUser principal = CurrentUser.require();
        tx.executeWithoutResult(status -> {
            if (principal.isPlatformAdmin()) {
                PlatformAdmin admin = platformAdminRepository.findById(principal.id())
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
                verifyCurrent(currentPassword, admin.getPasswordHash());
                PasswordPolicy.validate(newPassword, admin.getEmail());
                admin.changePassword(passwordEncoder.encode(newPassword));
            } else {
                User user = userRepository.findById(principal.id())
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
                verifyCurrent(currentPassword, user.getPasswordHash());
                PasswordPolicy.validate(newPassword, user.getEmail());
                user.changePassword(passwordEncoder.encode(newPassword));
            }
            auditService.record(AuditAction.PASSWORD_CHANGE, principal.isPlatformAdmin() ? "PlatformAdmin" : "User",
                    principal.id(), Map.of("via", "self-service"));
        });
    }

    private void verifyCurrent(String currentPassword, String hash) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, hash)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Senha atual incorreta.");
        }
    }

    private AuthenticatedUser complete(LoginOutcome outcome, String email, String ip, HttpServletRequest request,
            HttpServletResponse response) {
        if (outcome.principal() == null) {
            loginAttempts.recordFailure(email, ip);
            auditService.recordIndependent(outcome.tenantId(), ActorType.ANONYMOUS, outcome.actorId(),
                    outcome.actorName(), AuditAction.LOGIN_FAILED, "Session", null, Map.of("email", mask(email)));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttempts.recordSuccess(email);
        sessionService.establish(outcome.principal(), request, response);
        auditService.recordIndependent(outcome.principal().tenantId(), ActorType.of(outcome.principal().type()),
                outcome.principal().id(), outcome.principal().name(), AuditAction.LOGIN, "Session", null, Map.of());
        return outcome.principal();
    }

    /** E-mail parcialmente mascarado para a auditoria de falhas (minimização de dados). */
    static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    private record LoginOutcome(AuthenticatedUser principal, UUID tenantId, UUID actorId, String actorName) {

        static LoginOutcome success(AuthenticatedUser principal) {
            return new LoginOutcome(principal, principal.tenantId(), principal.id(), principal.name());
        }

        static LoginOutcome failed(UUID tenantId, UUID actorId, String actorName) {
            return new LoginOutcome(null, tenantId, actorId, actorName);
        }
    }
}
