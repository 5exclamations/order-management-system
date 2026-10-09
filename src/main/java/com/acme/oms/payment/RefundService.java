package com.acme.oms.payment;

import com.acme.oms.audit.AuditService;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.IdempotencyService;
import com.acme.oms.common.Idempotent;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.event.EventPublisher;
import com.acme.oms.inventory.InventoryService;
import com.acme.oms.order.CustomerOrder;
import com.acme.oms.order.OrderRepository;
import com.acme.oms.order.OrderStatus;
import com.acme.oms.payment.PaymentDtos.RefundResponse;
import com.acme.oms.payment.PaymentGateway.RefundResult;
import com.acme.oms.security.AuthUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund workflow, split in two so the slow, failure-prone part is decoupled from the HTTP request:
 * <ol>
 *   <li>{@code request}: validates, reserves the refundable balance, stores a PENDING refund and emits
 *       REFUND_REQUESTED through the outbox (one transaction).</li>
 *   <li>{@code process}: runs from the Kafka consumer, calls the gateway, settles the refund and, when the order
 *       is fully refunded, moves it to REFUNDED. Safe to run more than once for the same refund.</li>
 * </ol>
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final String IDEMPOTENCY_TYPE = "REFUND";

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentGateway gateway;
    private final InventoryService inventory;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final EventPublisher events;

    public RefundService(OrderRepository orders, PaymentRepository payments, RefundRepository refunds,
                         PaymentGateway gateway, InventoryService inventory, IdempotencyService idempotency,
                         AuditService audit, EventPublisher events) {
        this.orders = orders;
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
    }

    /** Staff-initiated refund (full or partial) of a paid order. */
    @RetryOnConflict
    @Transactional
    public Idempotent<RefundResponse> request(UUID orderId, BigDecimal amount, String reason, String idempotencyKey, AuthUser actor) {
        String scope = actor.userId().toString();
        String hash = IdempotencyService.hash(orderId, amount, reason);
        Optional<UUID> replay = idempotency.findReplay(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE);
        if (replay.isPresent()) {
            return new Idempotent<>(RefundResponse.from(refunds.findById(replay.get()).orElseThrow()), true);
        }

        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
        if (!order.getStatus().isRefundable()) {
            throw ApiException.conflict("ORDER_NOT_REFUNDABLE",
                    "Only PAID, SHIPPED or DELIVERED orders can be refunded; this order is " + order.getStatus());
        }
        Payment payment = payments.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> ApiException.conflict("PAYMENT_NOT_FOUND", "Order has no successful payment"));

        Refund refund = createRefund(payment, amount == null ? payment.refundable() : amount, reason, actor.username());
        idempotency.record(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE, refund.getId());
        return new Idempotent<>(RefundResponse.from(refund), false);
    }

    /** Called from within the cancel transaction: refund whatever is still refundable of a PAID order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestForCancellation(CustomerOrder order) {
        Payment payment = payments.findByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> ApiException.conflict("PAYMENT_NOT_FOUND", "Paid order has no successful payment"));
        if (payment.refundable().signum() > 0) {
            createRefund(payment, payment.refundable(), "Order cancelled", com.acme.oms.security.SecurityUtils.actorName());
        }
    }

    private Refund createRefund(Payment payment, BigDecimal amount, String reason, String requestedBy) {
        payment.commitRefund(amount); // validates against the refundable balance; bumps the payment version
        payments.saveAndFlush(payment);
        Refund refund = refunds.save(new Refund(payment.getOrderId(), payment.getId(), amount, reason, requestedBy));
        audit.record("REFUND_REQUESTED", "Order", payment.getOrderId(), "refund=" + refund.getId() + " amount=" + amount);
        events.publish("REFUND_REQUESTED", payment.getOrderId(),
                Map.of("refundId", refund.getId(), "orderId", payment.getOrderId(), "amount", amount));
        return refund;
    }

    /**
     * Settles a refund with the gateway. Driven by Kafka (at-least-once), so it must tolerate duplicates:
     * anything that is no longer PENDING is skipped, and the versioned rows make a truly concurrent duplicate fail
     * on flush and be redelivered, at which point it is skipped.
     */
    @Transactional
    public void process(UUID refundId) {
        Refund refund = refunds.findById(refundId).orElse(null);
        if (refund == null) {
            log.warn("Refund {} not found; ignoring event", refundId);
            return;
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            log.info("Refund {} already {}; skipping duplicate event", refundId, refund.getStatus());
            return;
        }
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
        RefundResult result = gateway.refund(payment.getGatewayReference(), refund.getAmount());

        if (result.succeeded()) {
            refund.complete(result.reference());
            refunds.saveAndFlush(refund);
            audit.record("REFUND_COMPLETED", "Order", refund.getOrderId(), "refund=" + refundId + " amount=" + refund.getAmount());
            events.publish("REFUND_COMPLETED", refund.getOrderId(),
                    Map.of("refundId", refundId, "orderId", refund.getOrderId(), "amount", refund.getAmount()));
            settleOrderIfFullyRefunded(refund.getOrderId());
        } else {
            refund.fail(result.failureReason());
            payment.releaseRefund(refund.getAmount());
            payments.save(payment);
            refunds.saveAndFlush(refund);
            audit.record("REFUND_FAILED", "Order", refund.getOrderId(), "refund=" + refundId + " reason=" + result.failureReason());
            events.publish("REFUND_FAILED", refund.getOrderId(),
                    Map.of("refundId", refundId, "orderId", refund.getOrderId(), "reason", result.failureReason()));
        }
    }

    private void settleOrderIfFullyRefunded(UUID orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (!order.getStatus().isRefundable()) {
            return; // e.g. CANCELLED: the refund is just money back, the order is already closed
        }
        BigDecimal refunded = refunds.sumByOrderAndStatus(orderId, RefundStatus.COMPLETED);
        if (refunded.compareTo(order.getTotal()) >= 0) {
            boolean stockStillHeld = order.getStatus() == OrderStatus.PAID;
            order.transitionTo(OrderStatus.REFUNDED);
            if (stockStillHeld) {
                inventory.releaseForOrder(orderId); // never shipped, so the stock goes back on sale
            }
            orders.saveAndFlush(order);
            audit.record("ORDER_REFUNDED", "Order", orderId, "total=" + order.getTotal());
            events.publish("ORDER_REFUNDED", orderId, Map.of("orderId", orderId, "total", order.getTotal()));
        }
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listForOrder(UUID orderId) {
        return refunds.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(RefundResponse::from).toList();
    }
}
