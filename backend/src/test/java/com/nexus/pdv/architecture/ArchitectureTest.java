package com.nexus.pdv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.pdv.shared.access.RequiresFeature;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.access.RequiresTenantAdmin;
import com.nexus.pdv.shared.access.TenantAuthenticated;
import com.nexus.pdv.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Regras arquiteturais que protegem a segurança contra regressões:
 * todo endpoint de tenant declara política de acesso e toda tabela de tenant é filtrada.
 */
class ArchitectureTest extends IntegrationTest {

    /** Tabelas com tenant_id que NÃO usam @TenantId por decisão explícita (acesso controlado de outra forma). */
    private static final Set<String> TENANT_COLUMN_EXCEPTIONS = Set.of(
            "audit_logs",            // inclui ações da plataforma (tenant nulo); filtrada explicitamente por tenantId
            "tenant_settings",       // chave primária = tenant_id da sessão
            "tenant_subscriptions",  // administrada pelo Super Admin; tenant lê a própria via contexto
            "subscription_invoices", // billing (Super Admin)
            "tenant_feature_overrides", "tenant_limit_overrides", "tenant_counters");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTenantEndpointDeclaresAnAccessPolicy() {
        List<String> unprotected = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            boolean tenantApi = info.getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/")
                    && !pattern.startsWith("/api/auth") && !pattern.startsWith("/api/super-admin")
                    && !pattern.startsWith("/api/billing/webhooks"));
            if (tenantApi && !hasPolicy(method)) {
                unprotected.add(info + " -> " + method);
            }
        });
        assertThat(unprotected).as("Endpoints de tenant sem @RequiresPermission/@RequiresFeature/@TenantAuthenticated")
                .isEmpty();
    }

    @Test
    void everyEntityOnATenantTableIsFilteredByTenantId() {
        List<String> tablesWithTenant = jdbc.queryForList(
                "SELECT table_name FROM information_schema.columns WHERE column_name = 'tenant_id' AND table_schema = 'public'",
                String.class);
        List<String> violations = new ArrayList<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            Table table = type.getAnnotation(Table.class);
            if (table == null || !tablesWithTenant.contains(table.name())
                    || TENANT_COLUMN_EXCEPTIONS.contains(table.name())) {
                continue;
            }
            if (!hasTenantIdField(type)) {
                violations.add(type.getSimpleName() + " (" + table.name() + ")");
            }
        }
        assertThat(violations).as("Entidades com tenant_id sem @TenantId").isEmpty();
    }

    private static boolean hasPolicy(HandlerMethod method) {
        List<Class<? extends Annotation>> policies = List.of(RequiresPermission.class, RequiresFeature.class,
                RequiresTenantAdmin.class, TenantAuthenticated.class);
        return policies.stream().anyMatch(policy ->
                AnnotatedElementUtils.hasAnnotation(method.getMethod(), policy)
                        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), policy));
    }

    private static boolean hasTenantIdField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(TenantId.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
