package com.acme.oms.common;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Query("select k from IdempotencyKey k where k.scope = :scope and k.key = :key")
    Optional<IdempotencyKey> find(@Param("scope") String scope, @Param("key") String key);
}
