package com.acme.oms.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /** SELECT ... FOR UPDATE; used where an irreversible external side effect (charging) must not run twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select o from CustomerOrder o
            where (:status is null or o.status = :status)
              and (:customerId is null or o.customerId = :customerId)
            """)
    Page<CustomerOrder> search(@Param("status") OrderStatus status, @Param("customerId") UUID customerId, Pageable pageable);

    @Query("select o.id from CustomerOrder o where o.status = :status and o.createdAt < :cutoff")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status, @Param("cutoff") Instant cutoff);
}
