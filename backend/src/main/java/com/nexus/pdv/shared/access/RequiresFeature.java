package com.nexus.pdv.shared.access;

import com.nexus.pdv.plan.domain.FeatureCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Exige que o tenant possua as features (o que a EMPRESA contratou). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresFeature {

    FeatureCode[] value();
}
