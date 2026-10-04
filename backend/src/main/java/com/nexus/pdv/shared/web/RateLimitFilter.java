package com.nexus.pdv.shared.web;

import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.JsonSecurityErrorHandlers;
import com.nexus.pdv.shared.security.NexusSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de requisições por IP (janela fixa de 1 minuto) para a API — mitiga abuso e
 * varredura. Estado em memória por instância; atrás de proxy, configure
 * {@code server.forward-headers-strategy} para obter o IP real.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED = 50_000;

    private final NexusSecurityProperties.RateLimit config;
    private final JsonSecurityErrorHandlers errorWriter;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(NexusSecurityProperties properties, JsonSecurityErrorHandlers errorWriter, Clock clock) {
        this.config = properties.getRateLimit();
        this.errorWriter = errorWriter;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.isEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.millis() / 60_000;
        if (windows.size() > MAX_TRACKED) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
        }
        Window window = windows.compute(request.getRemoteAddr(),
                (ip, current) -> current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > config.getRequestsPerMinute()) {
            response.setHeader("Retry-After", "60");
            errorWriter.write(response, ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
