package com.acme.oms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.oms.inventory.Inventory;
import com.acme.oms.inventory.InventoryRepository;
import com.acme.oms.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryRepository inventories;
    @Autowired TransactionTemplate tx;

    @Test
    void failedOrderRollsBackEverything() {
        String[] c = newCustomer();
        UUID plenty = newProduct("5.00", 10);
        UUID scarce = newProduct("5.00", 1);
        long ordersBefore = jdbc.queryForObject("select count(*) from orders", Long.class);
        long outboxBefore = jdbc.queryForObject("select count(*) from outbox_events", Long.class);
        long auditBefore = jdbc.queryForObject("select count(*) from audit_logs where action = 'ORDER_CREATED'", Long.class);

        // the "plenty" line is reserved first (ids sorted), the "scarce" line then fails: nothing may remain
        Resp r = order(c[0], key(), plenty, 1, scarce, 5);
        UUID[] sorted = {plenty, scarce};
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.text("code")).isEqualTo("INSUFFICIENT_STOCK");

        assertThat(stock(plenty).path("reserved").asInt()).isZero();
        assertThat(stock(scarce).path("reserved").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class)).isEqualTo(ordersBefore);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isEqualTo(outboxBefore);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ORDER_CREATED'", Long.class)).isEqualTo(auditBefore);
        assertThat(sorted).hasSize(2);
    }

    @Test
    void orderAuditAndOutboxCommitTogether() {
        String[] c = newCustomer();
        UUID p = newProduct("5.00", 3);
        Object id = order(c[0], key(), p, 1).id();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action = 'ORDER_CREATED'", Long.class, id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?::uuid and event_type = 'ORDER_CREATED'", Long.class, id.toString())).isEqualTo(1);
    }

    @Test
    void optimisticLockDetectsLostUpdate() {
        UUID p = newProduct("5.00", 10);
        Inventory first = inventories.findById(p).orElseThrow();
        Inventory stale = inventories.findById(p).orElseThrow(); // detached copies of the same row/version

        first.reserve(1);
        inventories.saveAndFlush(first);

        stale.reserve(1);
        assertThatThrownBy(() -> inventories.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(inventories.findById(p).orElseThrow().getReserved()).isEqualTo(1);
    }

    @Test
    void databaseRejectsReservedAboveOnHand() {
        UUID p = newProduct("5.00", 2);
        assertThatThrownBy(() -> jdbc.update("update inventory set reserved = 3 where product_id = ?", p))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSkuIsRejected() {
        String sku = "DUP-" + UUID.randomUUID();
        var body = java.util.Map.of("sku", sku, "name", "n", "price", 1);
        assertThat(post("/api/v1/products", staffToken, body).status()).isEqualTo(201);
        assertThat(post("/api/v1/products", staffToken, body).status()).isEqualTo(409);
    }
}
