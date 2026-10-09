package com.acme.oms.support;

import com.acme.oms.auth.AppUser;
import com.acme.oms.auth.AppUserRepository;
import com.acme.oms.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL + Kafka in containers, started once and shared by every integration test class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    public record Resp(int status, JsonNode body) {
        public String text(String field) { return body.path(field).asText(); }
        public UUID id() { return UUID.fromString(body.path("id").asText()); }
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected AppUserRepository users;
    @Autowired protected PasswordEncoder encoder;

    protected String adminToken;
    protected String staffToken;

    @BeforeEach
    void authenticateStaff() {
        if (adminToken == null) {
            adminToken = staffLogin("admin-" + UUID.randomUUID() + "@t.io", Role.ADMIN);
            staffToken = staffLogin("staff-" + UUID.randomUUID() + "@t.io", Role.STAFF);
        }
    }

    private String staffLogin(String username, Role role) {
        users.save(new AppUser(username, encoder.encode("Passw0rd!x"), role, null));
        return call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("username", username, "password", "Passw0rd!x"), null)
                .text("accessToken");
    }

    protected Resp call(HttpMethod m, String path, String token, Object body, String idemKey) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        if (idemKey != null) h.set("Idempotency-Key", idemKey);
        var res = rest.exchange(path, m, new HttpEntity<>(body, h), JsonNode.class);
        return new Resp(res.getStatusCode().value(), res.getBody());
    }

    protected Resp get(String path, String token) { return call(HttpMethod.GET, path, token, null, null); }
    protected Resp post(String path, String token, Object body) { return call(HttpMethod.POST, path, token, body, null); }
    protected Resp post(String path, String token, Object body, String key) { return call(HttpMethod.POST, path, token, body, key); }

    /** Registers a new customer; returns {token, customerId}. */
    protected String[] newCustomer() {
        String email = "c-" + UUID.randomUUID() + "@t.io";
        Resp r = post("/api/v1/auth/register", null, Map.of("email", email, "password", "Passw0rd!x", "name", "Test Customer"));
        if (r.status() != 201) throw new IllegalStateException("register failed: " + r.body());
        return new String[] {r.text("accessToken"), r.text("customerId")};
    }

    protected UUID newProduct(String price, int stock) {
        Resp r = post("/api/v1/products", staffToken, Map.of("sku", "SKU-" + UUID.randomUUID(), "name", "Widget",
                "price", new BigDecimal(price), "initialStock", stock));
        if (r.status() != 201) throw new IllegalStateException("product failed: " + r.body());
        return r.id();
    }

    protected Resp order(String token, String key, Object... productIdQtyPairs) {
        var items = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < productIdQtyPairs.length; i += 2) {
            items.add(Map.of("productId", productIdQtyPairs[i].toString(), "quantity", productIdQtyPairs[i + 1]));
        }
        return post("/api/v1/orders", token, Map.of("items", items), key);
    }

    protected JsonNode stock(UUID productId) { return get("/api/v1/inventory/" + productId, staffToken).body(); }
    protected Resp pay(String token, Object orderId, String paymentToken) {
        return post("/api/v1/orders/" + orderId + "/payments", token, Map.of("paymentMethodToken", paymentToken), UUID.randomUUID().toString());
    }
    protected String key() { return UUID.randomUUID().toString(); }
}
