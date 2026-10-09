package com.acme.oms.inventory;

public enum ReservationStatus {
    /** Stock is set aside for an unshipped order. */
    HELD,
    /** Order shipped; stock left the warehouse. */
    CONSUMED,
    /** Order cancelled/expired/refunded before shipment; stock is available again. */
    RELEASED
}
