package com.nexus.pdv.auth.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.audit.domain.AuditLog.ActorType;
import com.nexus.pdv.auth.domain.PasswordResetToken;
import com.nexus.pdv.auth.infrastructure.PasswordResetTokenRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.shared.security.NexusSecurityProperties;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fluxo de redefinição de senha com token aleatório de uso único e expiração curta.
 * A resposta a "esqueci minha senha" nunca revela se o e-mail existe.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;
    private final AuditService auditService;
    private final NexusSecurityProperties.PasswordReset config;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder, PasswordResetNotifier notifier, AuditService auditService,
            NexusSecurityProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.auditService = auditService;
        this.config = properties.getPasswordReset();
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** "Esqueci minha senha": silencioso para e-mails inexistentes ou usuários inativos. */
    public void requestReset(String email) {
        String normalized = User.normalizeEmail(email);
        TenantContext.runAsSystem(() -> tx.executeWithoutResult(status ->
                userRepository.findByEmail(normalized).filter(User::isActive).ifPresent(user -> {
                    issueToken(user);
                    auditService.recordIndependent(user.getTenantId(), ActorType.ANONYMOUS, user.getId(), user.getName(),
                            AuditAction.PASSWORD_RESET_REQUEST, "User", user.getId(), Map.of());
                })));
    }

    /** Redefinição iniciada pelo administrador do tenant (deve rodar dentro da transação do chamador). */
    public void issueToken(User user) {
        tokenRepository.invalidateAllOfUser(user.getId(), clock.instant());
        String rawToken = generateToken();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(config.getTokenTtlMinutes()));
        tokenRepository.save(new PasswordResetToken(user.getId(), sha256(rawToken), expiresAt));
        String link = config.getFrontendBaseUrl() + "/redefinir-senha?token=" + rawToken;
        notifier.sendResetLink(user.getEmail(), user.getName(), link);
    }

    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        TenantContext.runAsSystem(() -> tx.executeWithoutResult(status -> {
            PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow(this::invalidToken);
            Instant now = clock.instant();
            if (!token.isUsable(now)) {
                throw invalidToken();
            }
            User user = userRepository.findById(token.getUserId()).filter(User::isActive).orElseThrow(this::invalidToken);
            PasswordPolicy.validate(newPassword, user.getEmail());
            user.changePassword(passwordEncoder.encode(newPassword));
            token.markUsed(now);
            tokenRepository.invalidateAllOfUser(user.getId(), clock.instant());
            auditService.recordIndependent(user.getTenantId(), ActorType.TENANT_USER, user.getId(), user.getName(),
                    AuditAction.PASSWORD_CHANGE, "User", user.getId(), Map.of("via", "reset-token"));
        }));
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Link de redefinição inválido ou expirado.");
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
