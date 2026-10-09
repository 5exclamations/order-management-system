package com.acme.oms.payment;

import com.acme.oms.common.ApiException;
import com.acme.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(nullable = false, length = 100)
    private String paymentMethod;
    @Column(length = 100)
    private String gatewayReference;
    @Column(length = 255)
    private String failureReason;

    /**
     * PENDING + COMPLETED refund amounts. Because this row is versioned, two concurrent refund requests that
     * both read the same value cannot both commit: the loser is retried and sees the updated balance.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundCommitted = BigDecimal.ZERO;

    protected Payment() {}

    private Payment(UUID orderId, BigDecimal amount, String paymentMethod) {
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }

    public static Payment succeeded(UUID orderId, BigDecimal amount, String method, String gatewayReference) {
        Payment p = new Payment(orderId, amount, method);
        p.status = PaymentStatus.SUCCEEDED;
        p.gatewayReference = gatewayReference;
        return p;
    }

    public static Payment failed(UUID orderId, BigDecimal amount, String method, String reason) {
        Payment p = new Payment(orderId, amount, method);
        p.status = PaymentStatus.FAILED;
        p.failureReason = reason;
        return p;
    }

    public BigDecimal refundable() {
        return amount.subtract(refundCommitted);
    }

    public void commitRefund(BigDecimal refundAmount) {
        if (status != PaymentStatus.SUCCEEDED) {
            throw ApiException.conflict("PAYMENT_NOT_REFUNDABLE", "Only successful payments can be refunded");
        }
        if (refundAmount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_REFUND_AMOUNT", "Refund amount must be positive");
        }
        if (refundAmount.compareTo(refundable()) > 0) {
            throw ApiException.unprocessable("REFUND_EXCEEDS_PAYMENT",
                    "Refund of " + refundAmount + " exceeds the refundable balance of " + refundable());
        }
        refundCommitted = refundCommitted.add(refundAmount);
    }

    /** A refund failed at the gateway: give its amount back to the refundable balance. */
    public void releaseRefund(BigDecimal refundAmount) {
        refundCommitted = refundCommitted.subtract(refundAmount);
    }

    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getGatewayReference() { return gatewayReference; }
    public String getFailureReason() { return failureReason; }
    public BigDecimal getRefundCommitted() { return refundCommitted; }
}
