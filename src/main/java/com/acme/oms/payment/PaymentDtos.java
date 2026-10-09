package com.acme.oms.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    /** Token from the payment provider (simulated: tok_visa, tok_declined, ...). Never a card number. */
    public record PayRequest(@NotBlank @Size(max = 100) String paymentMethodToken) {}

    /** amount omitted = refund whatever is still refundable. */
    public record RefundRequest(@DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @Size(max = 500) String reason) {}

    public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, PaymentStatus status, String paymentMethod,
                                  String failureReason, BigDecimal refundedOrPending, Instant createdAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getPaymentMethod(),
                    p.getFailureReason(), p.getRefundCommitted(), p.getCreatedAt());
        }
    }

    public record RefundResponse(UUID id, UUID orderId, UUID paymentId, BigDecimal amount, RefundStatus status,
                                 String reason, String failureReason, String requestedBy, Instant createdAt, Instant updatedAt) {
        static RefundResponse from(Refund r) {
            return new RefundResponse(r.getId(), r.getOrderId(), r.getPaymentId(), r.getAmount(), r.getStatus(),
                    r.getReason(), r.getFailureReason(), r.getRequestedBy(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
