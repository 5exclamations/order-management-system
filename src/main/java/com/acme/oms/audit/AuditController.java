package com.acme.oms.audit;

import com.acme.oms.common.PageResponse;
import com.acme.oms.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit")
@PreAuthorize(Roles.ADMIN)
public class AuditController {

    public record AuditLogResponse(UUID id, Instant occurredAt, String actor, String action, String entityType,
                                   String entityId, String details, String requestId) {
        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActor(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getRequestId());
        }
    }

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Search the audit trail (newest first)")
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String entityId,
                                                 @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(repository.search(entityType, entityId, pageable), AuditLogResponse::from);
    }
}
