package com.acme.oms.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
    List<InventoryReservation> findByOrderId(UUID orderId);
}
