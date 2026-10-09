package com.acme.oms.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    Optional<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
