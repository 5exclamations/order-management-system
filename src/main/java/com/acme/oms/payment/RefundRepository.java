package com.acme.oms.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.orderId = :orderId and r.status = :status")
    BigDecimal sumByOrderAndStatus(@Param("orderId") UUID orderId, @Param("status") RefundStatus status);
}
