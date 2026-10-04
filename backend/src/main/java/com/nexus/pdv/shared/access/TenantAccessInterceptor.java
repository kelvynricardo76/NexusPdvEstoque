package com.nexus.pdv.shared.access;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica, no backend, a regra de acesso efetivo para toda a API operacional do tenant:
 * <pre>
 * AUTENTICADO → TENANT ATIVO → ASSINATURA VÁLIDA → TENANT TEM FEATURE → USUÁRIO TEM PERMISSÃO
 * </pre>
 * A última etapa ("recurso pertence ao tenant") é garantida pelo filtro {@code @TenantId}.
 * Endpoints sem anotação de acesso são negados (fail-closed).
 */
@Component
public class TenantAccessInterceptor implements HandlerInterceptor {

    private final AccessContextService accessContextService;

    public TenantAccessInterceptor(AccessContextService accessContextService) {
        this.accessContextService = accessContextService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission permissions = find(method, RequiresPermission.class);
        RequiresFeature features = find(method, RequiresFeature.class);
        RequiresTenantAdmin tenantAdmin = find(method, RequiresTenantAdmin.class);
        TenantAuthenticated authenticated = find(method, TenantAuthenticated.class);
        if (permissions == null && features == null && tenantAdmin == null && authenticated == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        AccessContext context = accessContextService.current();
        switch (context.accessState()) {
            case TENANT_SUSPENDED -> throw new BusinessException(ErrorCode.TENANT_SUSPENDED);
            case SUBSCRIPTION_INACTIVE -> throw new BusinessException(ErrorCode.SUBSCRIPTION_INACTIVE);
            case OPERABLE -> {
            }
        }

        if (features != null) {
            for (FeatureCode feature : features.value()) {
                context.requireFeature(feature);
            }
        }
        if (tenantAdmin != null && !context.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (permissions != null) {
            checkPermissions(context, permissions);
        }
        return true;
    }

    private static void checkPermissions(AccessContext context, RequiresPermission annotation) {
        if (!annotation.any()) {
            for (Permission permission : annotation.value()) {
                context.require(permission);
            }
            return;
        }
        boolean featureMissing = false;
        for (Permission permission : annotation.value()) {
            if (permission.feature() != null && !context.hasFeature(permission.feature())) {
                featureMissing = true;
            } else if (context.hasPermission(permission)) {
                return;
            }
        }
        throw new BusinessException(featureMissing ? ErrorCode.FEATURE_NOT_AVAILABLE : ErrorCode.ACCESS_DENIED);
    }

    private static <A extends Annotation> A find(HandlerMethod method, Class<A> type) {
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
    }
}
