package com.acme.oms.inventory;

import com.acme.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** Explicit record of stock held for an order line; makes release/consume idempotent and auditable. */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.HELD;

    protected InventoryReservation() {}

    public InventoryReservation(UUID orderId, UUID productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void markConsumed() { this.status = ReservationStatus.CONSUMED; }
    public void markReleased() { this.status = ReservationStatus.RELEASED; }

    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
