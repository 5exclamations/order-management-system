package com.acme.oms.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Re-runs the whole annotated (transactional) method in a fresh transaction when it loses an optimistic-lock race.
 * Must be applied to the outermost transactional entry point; see {@link ConflictRetryAspect}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RetryOnConflict {
    int maxAttempts() default 8;
}
