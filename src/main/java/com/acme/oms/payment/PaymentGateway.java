package com.acme.oms.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Port to the payment provider. Only the simulator is implemented here; a real adapter would implement this. */
public interface PaymentGateway {

    /** @param idempotencyRef stable id for this attempt so a retried HTTP call to a real PSP cannot double-charge */
    ChargeResult charge(UUID idempotencyRef, BigDecimal amount, String paymentMethodToken);

    RefundResult refund(String chargeReference, BigDecimal amount);

    record ChargeResult(boolean approved, String reference, String declineReason) {}

    record RefundResult(boolean succeeded, String reference, String failureReason) {}
}
