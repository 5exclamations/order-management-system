package com.acme.oms.payment;

import com.acme.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private UUID paymentId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;
    @Column(length = 100)
    private String gatewayReference;
    @Column(length = 255)
    private String failureReason;
    @Column(nullable = false)
    private String requestedBy;

    protected Refund() {}

    public Refund(UUID orderId, UUID paymentId, BigDecimal amount, String reason, String requestedBy) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    public void complete(String gatewayReference) {
        this.status = RefundStatus.COMPLETED;
        this.gatewayReference = gatewayReference;
    }

    public void fail(String reason) {
        this.status = RefundStatus.FAILED;
        this.failureReason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getGatewayReference() { return gatewayReference; }
    public String getFailureReason() { return failureReason; }
    public String getRequestedBy() { return requestedBy; }
}
