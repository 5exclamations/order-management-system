package com.acme.oms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.oms.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyIT extends AbstractIntegrationTest {

    private <T> List<T> runConcurrently(int n, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<T> c = task.apply(i);
            futures.add(pool.submit(() -> { start.await(); return c.call(); }));
        }
        start.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    @Test
    void neverOversellsLastUnits() throws Exception {
        UUID p = newProduct("5.00", 5);
        List<String[]> customers = new ArrayList<>();
        for (int i = 0; i < 20; i++) customers.add(newCustomer());

        List<Resp> results = runConcurrently(20, i -> () -> order(customers.get(i)[0], key(), p, 1));

        long created = results.stream().filter(r -> r.status() == 201).count();
        long rejected = results.stream().filter(r -> r.status() == 409 && "INSUFFICIENT_STOCK".equals(r.text("code"))).count();
        assertThat(created).isEqualTo(5);
        assertThat(rejected).isEqualTo(15);
        assertThat(stock(p).path("reserved").asInt()).isEqualTo(5);
        assertThat(stock(p).path("available").asInt()).isZero();
    }

    @Test
    void sameIdempotencyKeyCreatesExactlyOneOrder() throws Exception {
        UUID p = newProduct("5.00", 50);
        String[] c = newCustomer();
        String key = key();

        List<Resp> results = runConcurrently(10, i -> () -> order(c[0], key, p, 2));

        assertThat(results).allMatch(r -> r.status() == 200 || r.status() == 201);
        assertThat(results.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(results.stream().map(Resp::id).distinct().count()).isEqualTo(1);
        assertThat(stock(p).path("reserved").asInt()).isEqualTo(2);
    }

    @Test
    void concurrentPaymentsChargeOnce() throws Exception {
        UUID p = newProduct("5.00", 5);
        String[] c = newCustomer();
        Object id = order(c[0], key(), p, 1).id();

        List<Resp> results = runConcurrently(6, i -> () -> pay(c[0], id, "tok_visa"));

        assertThat(results.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 409).count()).isEqualTo(5);
        assertThat(get("/api/v1/orders/" + id + "/payments", c[0]).body()).hasSize(1);
    }

    @Test
    void concurrentRefundsCannotExceedPayment() throws Exception {
        UUID p = newProduct("10.00", 5);
        String[] c = newCustomer();
        Object id = order(c[0], key(), p, 1).id();
        pay(c[0], id, "tok_visa");

        List<Resp> results = runConcurrently(5, i -> () -> post("/api/v1/orders/" + id + "/refunds", staffToken, Map.of("amount", 6), key()));

        assertThat(results.stream().filter(r -> r.status() == 202).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 422).count()).isEqualTo(4);
    }

    @Test
    void concurrentCancelAndPayLeavesConsistentState() throws Exception {
        UUID p = newProduct("10.00", 5);
        String[] c = newCustomer();
        Object id = order(c[0], key(), p, 1).id();

        runConcurrently(2, i -> () -> i == 0 ? pay(c[0], id, "tok_visa") : post("/api/v1/orders/" + id + "/cancel", c[0], null));

        String status = get("/api/v1/orders/" + id, c[0]).text("status");
        assertThat(status).isIn("PAID", "CANCELLED");
        if (status.equals("CANCELLED")) {
            assertThat(stock(p).path("reserved").asInt()).isZero();
        } else {
            assertThat(stock(p).path("reserved").asInt()).isEqualTo(1);
        }
    }
}
