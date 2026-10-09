package com.acme.oms.order;

import com.acme.oms.audit.AuditService;
import com.acme.oms.catalog.Product;
import com.acme.oms.catalog.ProductRepository;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.Idempotent;
import com.acme.oms.common.IdempotencyService;
import com.acme.oms.common.PageResponse;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.customer.Customer;
import com.acme.oms.customer.CustomerRepository;
import com.acme.oms.event.EventPublisher;
import com.acme.oms.inventory.InventoryService;
import com.acme.oms.order.OrderDtos.CreateOrderRequest;
import com.acme.oms.order.OrderDtos.OrderLineRequest;
import com.acme.oms.order.OrderDtos.OrderResponse;
import com.acme.oms.payment.RefundService;
import com.acme.oms.security.AuthUser;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order use cases. Each public mutating method is ONE database transaction covering the order, its stock
 * reservation, the audit entry and the outbox event, and is retried from scratch on an optimistic-lock conflict.
 */
@Service
public class OrderService {

    static final String IDEMPOTENCY_TYPE = "ORDER";

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final RefundService refunds;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final EventPublisher events;

    public OrderService(OrderRepository orders, CustomerRepository customers, ProductRepository products,
                        InventoryService inventory, RefundService refunds, IdempotencyService idempotency,
                        AuditService audit, EventPublisher events) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.inventory = inventory;
        this.refunds = refunds;
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
    }

    @RetryOnConflict
    @Transactional
    public Idempotent<OrderResponse> create(CreateOrderRequest request, String idempotencyKey, AuthUser actor) {
        UUID customerId = resolveCustomerId(request, actor);
        // Merge duplicate lines and sort so the request hash and the reservation order are deterministic.
        Map<UUID, Integer> lines = request.items().stream().collect(Collectors.toMap(
                OrderLineRequest::productId, OrderLineRequest::quantity, Integer::sum, TreeMap::new));

        String scope = actor.userId().toString();
        String hash = IdempotencyService.hash(customerId, lines);
        Optional<UUID> replay = idempotency.findReplay(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE);
        if (replay.isPresent()) {
            return new Idempotent<>(OrderResponse.from(find(replay.get())), true);
        }

        Customer customer = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer", customerId));
        if (!customer.isActive()) {
            throw ApiException.unprocessable("CUSTOMER_INACTIVE", "Customer account is deactivated");
        }

        Map<UUID, Product> catalog = products.findAllById(lines.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        CustomerOrder order = new CustomerOrder(customerId);
        for (Map.Entry<UUID, Integer> line : lines.entrySet()) {
            Product product = catalog.get(line.getKey());
            if (product == null) {
                throw ApiException.unprocessable("PRODUCT_NOT_FOUND", "Product not found: " + line.getKey());
            }
            if (!product.isActive()) {
                throw ApiException.unprocessable("PRODUCT_INACTIVE", "Product is not available: " + product.getSku());
            }
            order.addItem(product, line.getValue());
        }
        orders.saveAndFlush(order);

        // All-or-nothing: if any line is out of stock the exception rolls back the order and earlier reservations.
        lines.forEach((productId, qty) -> inventory.reserve(order.getId(), productId, qty));

        audit.record("ORDER_CREATED", "Order", order.getId(), "customer=" + customerId + " total=" + order.getTotal());
        events.publish("ORDER_CREATED", order.getId(),
                Map.of("orderId", order.getId(), "customerId", customerId, "total", order.getTotal()));
        idempotency.record(scope, idempotencyKey, hash, IDEMPOTENCY_TYPE, order.getId());
        return new Idempotent<>(OrderResponse.from(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AuthUser actor) {
        return OrderResponse.from(loadAuthorized(id, actor));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(OrderStatus status, UUID customerId, Pageable pageable, AuthUser actor) {
        // Customers are always scoped to themselves, whatever filter they send.
        UUID effectiveCustomer = actor.isStaff() ? customerId : actor.customerId();
        return PageResponse.of(orders.search(status, effectiveCustomer, pageable), OrderResponse::from);
    }

    /**
     * Cancels an unshipped order and returns its stock. If it was already paid, a full refund is queued
     * (processed asynchronously through the refund workflow).
     */
    @RetryOnConflict
    @Transactional
    public OrderResponse cancel(UUID id, String reason, AuthUser actor) {
        CustomerOrder order = loadAuthorized(id, actor);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE",
                    "Only PENDING or PAID orders can be cancelled; this order is " + order.getStatus());
        }
        return doCancel(order, reason == null || reason.isBlank() ? "Cancelled by " + actor.username() : reason, "ORDER_CANCELLED");
    }

    /** Scheduled job entry point: releases stock held by an order that was never paid. No-op if already moved on. */
    @RetryOnConflict
    @Transactional
    public boolean expire(UUID id) {
        CustomerOrder order = orders.findById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        doCancel(order, "Reservation expired: payment not received in time", "ORDER_EXPIRED");
        return true;
    }

    @RetryOnConflict
    @Transactional
    public OrderResponse ship(UUID id) {
        CustomerOrder order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order", id));
        order.transitionTo(OrderStatus.SHIPPED);
        inventory.consumeForOrder(id);
        orders.saveAndFlush(order);
        audit.record("ORDER_SHIPPED", "Order", id, null);
        events.publish("ORDER_SHIPPED", id, Map.of("orderId", id));
        return OrderResponse.from(order);
    }

    @RetryOnConflict
    @Transactional
    public OrderResponse deliver(UUID id) {
        CustomerOrder order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order", id));
        order.transitionTo(OrderStatus.DELIVERED);
        orders.saveAndFlush(order);
        audit.record("ORDER_DELIVERED", "Order", id, null);
        events.publish("ORDER_DELIVERED", id, Map.of("orderId", id));
        return OrderResponse.from(order);
    }

    /** Load with object-level authorization; other customers' orders are reported as not found. */
    @Transactional(readOnly = true)
    public CustomerOrder loadAuthorized(UUID id, AuthUser actor) {
        CustomerOrder order = find(id);
        if (!actor.canAccessCustomer(order.getCustomerId())) {
            throw ApiException.notFound("Order", id);
        }
        return order;
    }

    private OrderResponse doCancel(CustomerOrder order, String reason, String eventType) {
        boolean wasPaid = order.getStatus() == OrderStatus.PAID;
        if (wasPaid) {
            refunds.requestForCancellation(order);
        }
        order.cancel(reason);
        inventory.releaseForOrder(order.getId());
        orders.saveAndFlush(order);
        audit.record(eventType, "Order", order.getId(), "reason=" + reason + " refundQueued=" + wasPaid);
        events.publish(eventType, order.getId(), Map.of("orderId", order.getId(), "reason", reason, "refundQueued", wasPaid));
        return OrderResponse.from(order);
    }

    private CustomerOrder find(UUID id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Order", id));
    }

    private UUID resolveCustomerId(CreateOrderRequest request, AuthUser actor) {
        if (actor.isStaff()) {
            if (request.customerId() == null) {
                throw ApiException.badRequest("CUSTOMER_ID_REQUIRED", "customerId is required when placing an order on behalf of a customer");
            }
            return request.customerId();
        }
        if (actor.customerId() == null) {
            throw ApiException.forbidden("This account has no customer profile");
        }
        if (request.customerId() != null && !request.customerId().equals(actor.customerId())) {
            throw ApiException.forbidden("You can only place orders for yourself");
        }
        return actor.customerId();
    }
}
