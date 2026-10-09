package com.acme.oms.order;

import com.acme.oms.catalog.Product;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/** Aggregate root. (Named CustomerOrder because ORDER is a reserved word in SQL/JPQL.) */
@Entity
@Table(name = "orders")
public class CustomerOrder extends BaseEntity {

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {}

    public CustomerOrder(UUID customerId) {
        this.customerId = customerId;
    }

    /** Snapshots name and price so later catalog edits never change what the customer agreed to pay. */
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(this, product.getId(), product.getSku(), product.getName(), product.getPrice(), quantity);
        items.add(item);
        total = total.add(item.lineTotal());
    }

    public void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "Order " + getId() + " cannot move from " + status + " to " + next);
        }
        status = next;
    }

    public void cancel(String reason) {
        transitionTo(OrderStatus.CANCELLED);
        this.cancelReason = reason;
    }

    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getCancelReason() { return cancelReason; }
    public List<OrderItem> getItems() { return items; }
}
