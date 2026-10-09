package com.acme.oms.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderLineRequest(@NotNull UUID productId, @Min(1) @Max(10_000) int quantity) {}

    /** customerId is required for staff placing an order on a customer's behalf; ignored/validated for customers. */
    public record CreateOrderRequest(UUID customerId,
                                     @NotEmpty @Size(max = 100) List<@Valid @NotNull OrderLineRequest> items) {}

    public record CancelOrderRequest(@Size(max = 500) String reason) {}

    public record OrderItemResponse(UUID productId, String sku, String productName, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {}

    public record OrderResponse(UUID id, UUID customerId, OrderStatus status, BigDecimal total, String cancelReason,
                                List<OrderItemResponse> items, Instant createdAt, Instant updatedAt, Long version) {
        static OrderResponse from(CustomerOrder o) {
            return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotal(), o.getCancelReason(),
                    o.getItems().stream()
                            .map(i -> new OrderItemResponse(i.getProductId(), i.getSku(), i.getProductName(),
                                    i.getUnitPrice(), i.getQuantity(), i.lineTotal()))
                            .toList(),
                    o.getCreatedAt(), o.getUpdatedAt(), o.getVersion());
        }
    }
}
