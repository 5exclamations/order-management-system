package com.acme.oms.payment;

import com.acme.oms.common.Idempotent;
import com.acme.oms.order.OrderService;
import com.acme.oms.payment.PaymentDtos.PayRequest;
import com.acme.oms.payment.PaymentDtos.PaymentResponse;
import com.acme.oms.payment.PaymentDtos.RefundRequest;
import com.acme.oms.payment.PaymentDtos.RefundResponse;
import com.acme.oms.security.AuthUser;
import com.acme.oms.security.Roles;
import com.acme.oms.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderId}")
@Tag(name = "Payments & Refunds")
public class PaymentController {

    private final PaymentService payments;
    private final RefundService refunds;
    private final OrderService orders;

    public PaymentController(PaymentService payments, RefundService refunds, OrderService orders) {
        this.payments = payments;
        this.refunds = refunds;
        this.orders = orders;
    }

    @Operation(summary = "Pay a PENDING order (simulated gateway)",
            description = "Test tokens: tok_visa / tok_mastercard approve; tok_declined, tok_insufficient_funds decline; "
                    + "tok_refund_fails approves but any later refund fails. A decline returns 201 with status FAILED and the order stays PENDING.")
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> pay(@PathVariable UUID orderId,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody PayRequest request) {
        Idempotent<PaymentResponse> result = payments.pay(orderId, request.paymentMethodToken(), idempotencyKey, SecurityUtils.requireUser());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.value());
    }

    @Operation(summary = "Payment attempts for an order")
    @GetMapping("/payments")
    public List<PaymentResponse> listPayments(@PathVariable UUID orderId) {
        authorize(orderId);
        return payments.listForOrder(orderId);
    }

    @Operation(summary = "Request a full or partial refund (staff)",
            description = "Returns 202: the refund is PENDING and is settled asynchronously via Kafka. Omit amount to refund the remaining balance.")
    @PostMapping("/refunds")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<RefundResponse> refund(@PathVariable UUID orderId,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody RefundRequest request) {
        Idempotent<RefundResponse> result = refunds.request(orderId, request.amount(), request.reason(), idempotencyKey, SecurityUtils.requireUser());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result.value());
    }

    @Operation(summary = "Refunds for an order")
    @GetMapping("/refunds")
    public List<RefundResponse> listRefunds(@PathVariable UUID orderId) {
        authorize(orderId);
        return refunds.listForOrder(orderId);
    }

    private void authorize(UUID orderId) {
        AuthUser user = SecurityUtils.requireUser();
        orders.get(orderId, user); // throws 404 unless the caller may see this order
    }
}
