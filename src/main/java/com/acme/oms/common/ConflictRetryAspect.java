package com.acme.oms.common;

import jakarta.persistence.OptimisticLockException;
import java.util.concurrent.ThreadLocalRandom;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Optimistic locking detects lost updates; this aspect makes the loser try again on fresh data.
 * It runs OUTSIDE the transaction advice (higher precedence than the transaction interceptor), so each
 * attempt gets its own transaction and its own persistence context. After the attempts are exhausted the
 * last exception propagates and is reported to the client as 409 CONCURRENT_MODIFICATION.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ConflictRetryAspect {

    private static final Logger log = LoggerFactory.getLogger(ConflictRetryAspect.class);

    @Around("@annotation(retry)")
    public Object around(ProceedingJoinPoint pjp, RetryOnConflict retry) throws Throwable {
        int attempt = 1;
        while (true) {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                if (!isRetryable(t) || attempt >= retry.maxAttempts()) {
                    throw t;
                }
                log.debug("Conflict in {} (attempt {}/{}), retrying", pjp.getSignature().toShortString(), attempt, retry.maxAttempts());
                attempt++;
                Thread.sleep(ThreadLocalRandom.current().nextLong(2, 10L + attempt * 5L));
            }
        }
    }

    static boolean isRetryable(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof ObjectOptimisticLockingFailureException
                    || c instanceof OptimisticLockException
                    || c instanceof IdempotencyRaceException) {
                return true;
            }
        }
        return false;
    }
}
