package com.acme.oms.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several app instances relay in parallel without publishing the same row twice. */
    @Query(value = """
            select * from outbox_events where published_at is null
            order by created_at limit :limit for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
