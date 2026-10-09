package com.acme.oms.order;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The transition table is the single source of truth for what is legal; every status change
 * goes through {@link CustomerOrder#transitionTo(OrderStatus)}.
 *
 * <pre>
 * PENDING --pay--> PAID --ship--> SHIPPED --deliver--> DELIVERED
 *    |               |               |                     |
 *  cancel/expire   cancel         refund (full)         refund (full)
 *    v               v               v                     v
 * CANCELLED       CANCELLED        REFUNDED              REFUNDED      (PAID --refund(full)--> REFUNDED too)
 * </pre>
 */
public enum OrderStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CANCELLED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(PAID, CANCELLED));
        ALLOWED.put(PAID, EnumSet.of(SHIPPED, CANCELLED, REFUNDED));
        ALLOWED.put(SHIPPED, EnumSet.of(DELIVERED, REFUNDED));
        ALLOWED.put(DELIVERED, EnumSet.of(REFUNDED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public Set<OrderStatus> allowedTransitions() {
        return Set.copyOf(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** States in which money has been captured and a refund may be requested. */
    public boolean isRefundable() {
        return this == PAID || this == SHIPPED || this == DELIVERED;
    }
}
