package com.nexus.pdv.shared.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara explicitamente que o endpoint exige apenas usuário de tenant autenticado e tenant
 * operante, sem permissão específica. Endpoints de tenant sem nenhuma anotação de acesso são
 * bloqueados (fail-closed).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TenantAuthenticated {
}
