package com.acme.oms.payment;

import com.acme.oms.audit.AuditService;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.IdempotencyService;
import com.acme.oms.common.Idempotent;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.event.EventPublisher;
import com.acme.oms.order.CustomerOrder;
import com.acme.oms.order.OrderRepository;
import com.acme.oms.order.OrderStatus;
import com.acme.oms.payment.PaymentDtos.PaymentResponse;
import com.acme.oms.payment.PaymentGateway.ChargeResult;
import com.acme.oms.security.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final String IDEMPOTENCY_TYPE = "PAYMENT";

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final EventPublisher events;

    public PaymentService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway,
                          IdempotencyService idempotency, AuditService audit, EventPublisher events) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Charges a PENDING order. A declined card is a normal business outcome: the attempt is recorded
     * (status FAILED), the order stays PENDING with its stock still held, and the customer may try again.
     *
     * <p>The order row is locked with SELECT ... FOR UPDATE for the whole call. Charging is an irreversible
     * external side effect, so optimistic "detect and retry" is not enough here: two concurrent payment
     * requests must be serialised BEFORE the gateway is called, not after.
     */
    @RetryOnConflict
    @Transactional
    public Idempotent<PaymentResponse> pay(UUID orderId, String token, String idempotencyKey, AuthUser actor) {
        String scope = actor.userId().toString();
        String hash = IdempotencyService.hash(orderId, token);
        Optional<UUID> replay = idempotency.findReplay(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE);
        if (replay.isPresent()) {
            Payment existing = payments.findById(replay.get()).orElseThrow();
            return new Idempotent<>(PaymentResponse.from(existing), true);
        }

        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
        if (!actor.canAccessCustomer(order.getCustomerId())) {
            throw ApiException.notFound("Order", orderId);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE", "Only PENDING orders can be paid; this order is " + order.getStatus());
        }

        UUID attemptId = UUID.randomUUID();
        ChargeResult result = gateway.charge(attemptId, order.getTotal(), token);
        Payment payment;
        if (result.approved()) {
            payment = Payment.succeeded(orderId, order.getTotal(), token, result.reference());
            order.transitionTo(OrderStatus.PAID);
            orders.save(order);
            audit.record("PAYMENT_SUCCEEDED", "Order", orderId, "amount=" + order.getTotal());
            events.publish("ORDER_PAID", orderId, Map.of("orderId", orderId, "amount", order.getTotal()));
        } else {
            payment = Payment.failed(orderId, order.getTotal(), token, result.declineReason());
            audit.record("PAYMENT_FAILED", "Order", orderId, "reason=" + result.declineReason());
            events.publish("PAYMENT_FAILED", orderId, Map.of("orderId", orderId, "reason", result.declineReason()));
        }
        payments.saveAndFlush(payment);
        idempotency.record(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE, payment.getId());
        return new Idempotent<>(PaymentResponse.from(payment), false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(UUID orderId) {
        return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(PaymentResponse::from).toList();
    }
}
