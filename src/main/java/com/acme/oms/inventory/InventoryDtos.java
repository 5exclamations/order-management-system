package com.acme.oms.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record AdjustInventoryRequest(
            @NotNull Integer delta,
            @NotBlank @Size(max = 200) String reason) {}

    public record InventoryResponse(UUID productId, int onHand, int reserved, int available, Instant updatedAt, Long version) {
        static InventoryResponse from(Inventory i) {
            return new InventoryResponse(i.getProductId(), i.getOnHand(), i.getReserved(), i.available(), i.getUpdatedAt(), i.getVersion());
        }
    }
}
