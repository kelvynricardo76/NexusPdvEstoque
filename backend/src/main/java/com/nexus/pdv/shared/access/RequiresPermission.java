package com.nexus.pdv.shared.access;

import com.nexus.pdv.permission.domain.Permission;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige permissões do usuário. Para cada permissão, o tenant também precisa possuir a feature
 * associada ({@link Permission#feature()}), senão a resposta é FEATURE_NOT_AVAILABLE.
 * Por padrão todas são exigidas; {@code any = true} exige ao menos uma.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    Permission[] value();

    boolean any() default false;
}
