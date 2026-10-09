package com.acme.oms.order;

import com.acme.oms.common.Idempotent;
import com.acme.oms.common.PageResponse;
import com.acme.oms.order.OrderDtos.CancelOrderRequest;
import com.acme.oms.order.OrderDtos.CreateOrderRequest;
import com.acme.oms.order.OrderDtos.OrderResponse;
import com.acme.oms.security.Roles;
import com.acme.oms.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @Operation(summary = "Place an order and reserve stock",
            description = "Idempotent: replaying the same Idempotency-Key and body returns the original order with 200. "
                    + "Returns 409 INSUFFICIENT_STOCK if any line cannot be reserved; nothing is persisted in that case.")
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Parameter(description = "Unique per logical request, e.g. a UUID") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        Idempotent<OrderResponse> result = service.create(request, idempotencyKey, SecurityUtils.requireUser());
        if (result.replayed()) {
            return ResponseEntity.ok(result.value());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.value().id())).body(result.value());
    }

    @Operation(summary = "Get an order (staff, or its owner)")
    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return service.get(id, SecurityUtils.requireUser());
    }

    @Operation(summary = "List orders; customers only ever see their own")
    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) UUID customerId,
                                            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, customerId, pageable, SecurityUtils.requireUser());
    }

    @Operation(summary = "Cancel a PENDING or PAID order; a paid order is refunded automatically")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return service.cancel(id, request == null ? null : request.reason(), SecurityUtils.requireUser());
    }

    @Operation(summary = "Mark a PAID order as shipped; reserved stock is consumed (staff)")
    @PostMapping("/{id}/ship")
    @PreAuthorize(Roles.STAFF)
    public OrderResponse ship(@PathVariable UUID id) {
        return service.ship(id);
    }

    @Operation(summary = "Mark a SHIPPED order as delivered (staff)")
    @PostMapping("/{id}/deliver")
    @PreAuthorize(Roles.STAFF)
    public OrderResponse deliver(@PathVariable UUID id) {
        return service.deliver(id);
    }
}
