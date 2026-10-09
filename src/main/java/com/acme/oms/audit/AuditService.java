package com.acme.oms.audit;

import com.acme.oms.common.RequestIdFilter;
import com.acme.oms.security.SecurityUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit entries join the caller's transaction (MANDATORY): a business change and its audit record commit or
 * roll back together, so the log can never claim something happened that did not.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, String details) {
        repository.save(new AuditLog(SecurityUtils.actorName(), action, entityType, String.valueOf(entityId),
                details, MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
