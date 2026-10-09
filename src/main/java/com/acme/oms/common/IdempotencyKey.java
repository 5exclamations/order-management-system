package com.acme.oms.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String scope;

    @Column(name = "idem_key", nullable = false, length = 200)
    private String key;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 30)
    private String resourceType;

    @Column(nullable = false)
    private UUID resourceId;

    protected IdempotencyKey() {}

    public IdempotencyKey(String scope, String key, String requestHash, String resourceType, UUID resourceId) {
        this.scope = scope;
        this.key = key;
        this.requestHash = requestHash;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getRequestHash() { return requestHash; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
}
