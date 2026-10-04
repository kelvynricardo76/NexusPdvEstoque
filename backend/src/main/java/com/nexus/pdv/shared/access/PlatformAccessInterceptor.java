package com.nexus.pdv.shared.access;

import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Revalida a cada requisição que o SUPER_ADMIN da sessão continua ativo. */
@Component
public class PlatformAccessInterceptor implements HandlerInterceptor {

    private final PlatformAdminRepository repository;

    public PlatformAccessInterceptor(PlatformAdminRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        AuthenticatedUser principal = CurrentUser.find().orElse(null);
        if (principal == null) {
            return true; // rotas públicas de login (demais já barradas pelo Spring Security)
        }
        if (!principal.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        boolean active = repository.findById(principal.id()).map(admin -> admin.isActive()).orElse(false);
        if (!active) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Sua sessão foi encerrada. Entre novamente.");
        }
        return true;
    }
}
