package com.acme.oms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.oms.common.ApiException;
import com.acme.oms.payment.Payment;
import com.acme.oms.payment.PaymentGateway.ChargeResult;
import com.acme.oms.payment.SimulatedPaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

    @Test
    void refundsCannotExceedPayment() {
        Payment p = Payment.succeeded(UUID.randomUUID(), new BigDecimal("100.00"), "tok_visa", "ref");
        p.commitRefund(new BigDecimal("60.00"));
        assertThatThrownBy(() -> p.commitRefund(new BigDecimal("40.01"))).isInstanceOf(ApiException.class);
        p.commitRefund(new BigDecimal("40.00"));
        assertThat(p.refundable()).isEqualByComparingTo("0");
    }

    @Test
    void failedRefundReleasesBalance() {
        Payment p = Payment.succeeded(UUID.randomUUID(), new BigDecimal("50.00"), "tok_visa", "ref");
        p.commitRefund(new BigDecimal("50.00"));
        p.releaseRefund(new BigDecimal("50.00"));
        assertThat(p.refundable()).isEqualByComparingTo("50.00");
    }

    @Test
    void gatewayTokens() {
        BigDecimal a = BigDecimal.TEN;
        assertThat(gateway.charge(UUID.randomUUID(), a, "tok_visa").approved()).isTrue();
        ChargeResult declined = gateway.charge(UUID.randomUUID(), a, "tok_declined");
        assertThat(declined.approved()).isFalse();
        assertThat(gateway.charge(UUID.randomUUID(), a, "garbage").declineReason()).isEqualTo("invalid_payment_method");
        ChargeResult c = gateway.charge(UUID.randomUUID(), a, "tok_refund_fails");
        assertThat(gateway.refund(c.reference(), a).succeeded()).isFalse();
        assertThat(gateway.refund(gateway.charge(UUID.randomUUID(), a, "tok_visa").reference(), a).succeeded()).isTrue();
    }
}
