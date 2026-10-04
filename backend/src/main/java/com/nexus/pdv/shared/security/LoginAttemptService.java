package com.nexus.pdv.shared.security;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Proteção contra força bruta: bloqueia temporariamente por e-mail e por IP após falhas
 * consecutivas dentro da janela configurada. Estado em memória (por instância); para múltiplas
 * instâncias, substituir por armazenamento compartilhado.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private final NexusSecurityProperties.Login config;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(NexusSecurityProperties properties, Clock clock) {
        this.config = properties.getLogin();
        this.clock = clock;
    }

    public void assertNotBlocked(String email, String ip) {
        Instant now = clock.instant();
        if (isBlocked(emailKey(email), now) || isBlocked(ipKey(ip), now)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente novamente em alguns minutos.");
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now, window()));
        }
        register(emailKey(email), config.getMaxAttemptsPerEmail(), now);
        register(ipKey(ip), config.getMaxAttemptsPerIp(), now);
    }

    public void recordSuccess(String email) {
        attempts.remove(emailKey(email));
    }

    private boolean isBlocked(String key, Instant now) {
        Attempts current = attempts.get(key);
        return current != null && current.lockedUntil != null && current.lockedUntil.isAfter(now);
    }

    private void register(String key, int maxAttempts, Instant now) {
        attempts.compute(key, (k, current) -> {
            Attempts next = current == null || current.isExpired(now, window()) ? new Attempts(now) : current;
            next.failures++;
            if (next.failures >= maxAttempts) {
                next.lockedUntil = now.plus(window());
            }
            return next;
        });
    }

    private Duration window() {
        return Duration.ofMinutes(config.getLockMinutes());
    }

    private static String emailKey(String email) {
        return "e:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private static String ipKey(String ip) {
        return "i:" + (ip == null ? "" : ip);
    }

    private static final class Attempts {
        private final Instant windowStart;
        private int failures;
        private Instant lockedUntil;

        private Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }

        private boolean isExpired(Instant now, Duration window) {
            boolean lockOver = lockedUntil == null || !lockedUntil.isAfter(now);
            return lockOver && windowStart.plus(window).isBefore(now);
        }
    }
}
