package com.acme.oms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.acme.oms.order.OrderExpiryJob;
import com.acme.oms.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderWorkflowIT extends AbstractIntegrationTest {

    @Autowired OrderExpiryJob expiryJob;
    @Autowired JdbcTemplate jdbc;

    private String orderStatus(Object id, String token) { return get("/api/v1/orders/" + id, token).text("status"); }

    @Test
    void fullLifecycleWithAsyncRefund() {
        String[] c = newCustomer();
        UUID p = newProduct("25.00", 10);

        Resp created = order(c[0], key(), p, 2);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.text("status")).isEqualTo("PENDING");
        assertThat(created.body().path("total").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(stock(p).path("reserved").asInt()).isEqualTo(2);
        assertThat(stock(p).path("available").asInt()).isEqualTo(8);

        Object id = created.id();
        assertThat(pay(c[0], id, "tok_visa").text("status")).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(id, c[0])).isEqualTo("PAID");

        assertThat(post("/api/v1/orders/" + id + "/ship", staffToken, null).text("status")).isEqualTo("SHIPPED");
        assertThat(stock(p).path("onHand").asInt()).isEqualTo(8);
        assertThat(stock(p).path("reserved").asInt()).isZero();
        assertThat(post("/api/v1/orders/" + id + "/deliver", staffToken, null).text("status")).isEqualTo("DELIVERED");

        Resp refund = post("/api/v1/orders/" + id + "/refunds", staffToken, Map.of("reason", "damaged"), key());
        assertThat(refund.status()).isEqualTo(202);
        assertThat(refund.text("status")).isEqualTo("PENDING");

        // settled asynchronously: outbox -> Kafka -> consumer -> gateway
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(get("/api/v1/orders/" + id + "/refunds", staffToken).body().get(0).path("status").asText()).isEqualTo("COMPLETED");
            assertThat(orderStatus(id, c[0])).isEqualTo("REFUNDED");
        });

        var audit = get("/api/v1/audit-logs?entityType=Order&entityId=" + id, adminToken).body().path("content");
        assertThat(audit.findValuesAsText("action")).contains("ORDER_CREATED", "PAYMENT_SUCCEEDED", "ORDER_SHIPPED", "REFUND_COMPLETED", "ORDER_REFUNDED");
    }

    @Test
    void declinedPaymentKeepsOrderPendingAndRetrySucceeds() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 1).id();
        assertThat(pay(c[0], id, "tok_declined").text("status")).isEqualTo("FAILED");
        assertThat(orderStatus(id, c[0])).isEqualTo("PENDING");
        assertThat(stock(p).path("reserved").asInt()).isEqualTo(1);
        assertThat(pay(c[0], id, "tok_visa").text("status")).isEqualTo("SUCCEEDED");
        assertThat(pay(c[0], id, "tok_visa").status()).isEqualTo(409); // already paid
    }

    @Test
    void cancellingPendingOrderReleasesStock() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 3).id();
        assertThat(post("/api/v1/orders/" + id + "/cancel", c[0], Map.of("reason", "changed mind")).text("status")).isEqualTo("CANCELLED");
        assertThat(stock(p).path("reserved").asInt()).isZero();
        assertThat(post("/api/v1/orders/" + id + "/cancel", c[0], null).status()).isEqualTo(409);
    }

    @Test
    void cancellingPaidOrderTriggersAutomaticRefund() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 1).id();
        pay(c[0], id, "tok_visa");
        assertThat(post("/api/v1/orders/" + id + "/cancel", c[0], null).text("status")).isEqualTo("CANCELLED");
        assertThat(stock(p).path("available").asInt()).isEqualTo(5);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(get("/api/v1/orders/" + id + "/refunds", c[0]).body().get(0).path("status").asText()).isEqualTo("COMPLETED"));
    }

    @Test
    void failedRefundIsRecordedAndBalanceReleased() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 1).id();
        pay(c[0], id, "tok_refund_fails");
        post("/api/v1/orders/" + id + "/refunds", staffToken, Map.of("amount", 4), key());
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(get("/api/v1/orders/" + id + "/refunds", staffToken).body().get(0).path("status").asText()).isEqualTo("FAILED"));
        assertThat(orderStatus(id, c[0])).isEqualTo("PAID");
        assertThat(post("/api/v1/orders/" + id + "/refunds", staffToken, Map.of("amount", 10), key()).status()).isEqualTo(202);
    }

    @Test
    void refundCannotExceedPaidAmount() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 1).id();
        pay(c[0], id, "tok_visa");
        assertThat(post("/api/v1/orders/" + id + "/refunds", staffToken, Map.of("amount", 10.01), key()).status()).isEqualTo(422);
    }

    @Test
    void expiryReleasesStockOfUnpaidOrders() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(c[0], key(), p, 2).id();
        jdbc.update("update orders set created_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), id);
        assertThat(expiryJob.expireOlderThan(Instant.now().minusSeconds(900))).isGreaterThanOrEqualTo(1);
        assertThat(orderStatus(id, c[0])).isEqualTo("CANCELLED");
        assertThat(stock(p).path("reserved").asInt()).isZero();
    }

    @Test
    void rbacAndOwnership() {
        String[] a = newCustomer();
        String[] b = newCustomer();
        UUID p = newProduct("10.00", 5);
        Object id = order(a[0], key(), p, 1).id();

        assertThat(get("/api/v1/orders/" + id, null).status()).isEqualTo(401);
        assertThat(get("/api/v1/orders/" + id, b[0]).status()).isEqualTo(404);
        assertThat(post("/api/v1/products", a[0], Map.of("sku", "X1", "name", "n", "price", 1)).status()).isEqualTo(403);
        assertThat(post("/api/v1/orders/" + id + "/ship", a[0], null).status()).isEqualTo(403);
        assertThat(get("/api/v1/audit-logs", staffToken).status()).isEqualTo(403);
        assertThat(get("/api/v1/inventory/" + p, a[0]).status()).isEqualTo(403);
        assertThat(get("/api/v1/orders", b[0]).body().path("content")).isEmpty();
        assertThat(post("/api/v1/orders", b[0], Map.of("customerId", a[1], "items", java.util.List.of(Map.of("productId", p.toString(), "quantity", 1))), key()).status()).isEqualTo(403);
    }

    @Test
    void idempotencyKeyReplaysAndRejectsDifferentBody() {
        String[] c = newCustomer();
        UUID p = newProduct("10.00", 5);
        String key = key();
        Resp first = order(c[0], key, p, 1);
        Resp second = order(c[0], key, p, 1);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(stock(p).path("reserved").asInt()).isEqualTo(1);
        assertThat(order(c[0], key, p, 2).status()).isEqualTo(422);
        assertThat(post("/api/v1/orders", c[0], Map.of("items", java.util.List.of())).status()).isEqualTo(400);
    }

    @Test
    void validationErrorsUseProblemFormat() {
        Resp r = post("/api/v1/auth/register", null, Map.of("email", "nope", "password", "x", "name", ""));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.text("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(r.body().path("errors").has("email")).isTrue();
    }
}
