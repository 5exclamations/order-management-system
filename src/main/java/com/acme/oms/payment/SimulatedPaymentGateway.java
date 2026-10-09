package com.acme.oms.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a card processor, driven by the payment method token:
 * <ul>
 *   <li>{@code tok_visa}, {@code tok_mastercard} - approved</li>
 *   <li>{@code tok_declined} - declined (generic)</li>
 *   <li>{@code tok_insufficient_funds} - declined (insufficient funds)</li>
 *   <li>{@code tok_refund_fails} - charge approved, but any later refund fails at the gateway</li>
 *   <li>anything else - declined as an invalid token</li>
 * </ul>
 * Never send real card numbers to this API; clients exchange cards for tokens with the PSP.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String NO_REFUND_PREFIX = "sim_norefund_";

    @Override
    public ChargeResult charge(UUID idempotencyRef, BigDecimal amount, String token) {
        return switch (token == null ? "" : token) {
            case "tok_visa", "tok_mastercard" -> new ChargeResult(true, "sim_ch_" + idempotencyRef, null);
            case "tok_refund_fails" -> new ChargeResult(true, NO_REFUND_PREFIX + idempotencyRef, null);
            case "tok_declined" -> new ChargeResult(false, null, "card_declined");
            case "tok_insufficient_funds" -> new ChargeResult(false, null, "insufficient_funds");
            default -> new ChargeResult(false, null, "invalid_payment_method");
        };
    }

    @Override
    public RefundResult refund(String chargeReference, BigDecimal amount) {
        if (chargeReference == null || chargeReference.startsWith(NO_REFUND_PREFIX)) {
            return new RefundResult(false, null, "gateway_refund_rejected");
        }
        return new RefundResult(true, "sim_re_" + UUID.randomUUID(), null);
    }
}
