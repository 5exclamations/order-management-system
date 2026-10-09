package com.acme.oms.audit;

import com.acme.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Append-only record of who did what to which entity. createdAt is the event time. */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private String actor;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(nullable = false, length = 40)
    private String entityType;
    @Column(nullable = false, length = 64)
    private String entityId;
    @Column(length = 2000)
    private String details;
    @Column(length = 64)
    private String requestId;

    protected AuditLog() {}

    public AuditLog(String actor, String action, String entityType, String entityId, String details, String requestId) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details != null && details.length() > 2000 ? details.substring(0, 2000) : details;
        this.requestId = requestId;
    }

    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public String getRequestId() { return requestId; }
}
