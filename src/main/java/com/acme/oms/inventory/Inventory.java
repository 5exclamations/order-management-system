package com.acme.oms.inventory;

import com.acme.oms.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Stock of one product. {@code available = onHand - reserved}.
 * The {@code @Version} column means two concurrent reservations can never both succeed from the same snapshot:
 * the loser's UPDATE matches zero rows and the whole business transaction is retried against fresh data.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private UUID productId;

    @Version
    private Long version;

    @Column(nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Inventory() {}

    public Inventory(UUID productId, int onHand) {
        this.productId = productId;
        this.onHand = onHand;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public int available() {
        return onHand - reserved;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (available() < quantity) {
            throw ApiException.conflict("INSUFFICIENT_STOCK",
                    "Insufficient stock for product " + productId + ": requested " + quantity + ", available " + available());
        }
        reserved += quantity;
    }

    public void release(int quantity) {
        requirePositive(quantity);
        if (reserved < quantity) {
            throw new IllegalStateException("Cannot release " + quantity + " units; only " + reserved + " reserved");
        }
        reserved -= quantity;
    }

    /** Reserved stock physically leaves the warehouse (shipment). */
    public void consume(int quantity) {
        requirePositive(quantity);
        if (reserved < quantity) {
            throw new IllegalStateException("Cannot consume " + quantity + " units; only " + reserved + " reserved");
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    /** Manual correction: receiving goods (+) or shrinkage (-). Cannot dip into reserved stock. */
    public void adjust(int delta) {
        if (delta == 0) {
            throw ApiException.badRequest("INVALID_ADJUSTMENT", "Adjustment must not be zero");
        }
        if (onHand + delta < reserved) {
            throw ApiException.conflict("ADJUSTMENT_BELOW_RESERVED",
                    "Cannot reduce on-hand to " + (onHand + delta) + "; " + reserved + " units are reserved");
        }
        onHand += delta;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public UUID getProductId() { return productId; }
    public Long getVersion() { return version; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public Instant getUpdatedAt() { return updatedAt; }
}
