# Order Management System

Spring Boot 3.5 / Java 21 order management backend: customers, catalog, inventory with stock reservation, an order
state machine, simulated payments, cancellation and an event-driven refund workflow, with audit logging.

Stack: Spring Security (JWT + RBAC), Spring Data JPA, PostgreSQL, Flyway, Kafka, springdoc OpenAPI,
JUnit 5, Testcontainers, Docker, GitHub Actions.

## Quick start

```bash
docker compose up -d                       # Postgres :5433, Kafka :29092
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html. The `dev` profile seeds `admin@oms.local` and `staff@oms.local`
(password from `DEV_SEED_PASSWORD`, default `DevPassw0rd!`) and three products. Run everything in containers with
`docker compose --profile app up --build`.

## Try it

```bash
T=$(curl -s localhost:8080/api/v1/auth/register -H 'content-type: application/json' \
  -d '{"email":"me@example.com","password":"Passw0rd!x","name":"Me"}' | jq -r .accessToken)
S=$(curl -s localhost:8080/api/v1/auth/login -H 'content-type: application/json' \
  -d '{"username":"staff@oms.local","password":"DevPassw0rd!"}' | jq -r .accessToken)
P=$(curl -s localhost:8080/api/v1/products -H "authorization: Bearer $T" | jq -r '.content[0].id')
O=$(curl -s localhost:8080/api/v1/orders -H "authorization: Bearer $T" -H 'content-type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d "{\"items\":[{\"productId\":\"$P\",\"quantity\":2}]}" | jq -r .id)
curl -s localhost:8080/api/v1/orders/$O/payments -H "authorization: Bearer $T" -H 'content-type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"paymentMethodToken":"tok_visa"}'
curl -s -XPOST localhost:8080/api/v1/orders/$O/ship -H "authorization: Bearer $S"
```

## Tests

```bash
mvn test      # unit tests, no Docker
mvn verify    # + integration tests: real PostgreSQL and Kafka via Testcontainers (needs Docker)
```

On macOS with Colima: `export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
Docker Engine 29 needs Testcontainers 1.21.4+ (set in `pom.xml`).

| Suite | What it proves |
|---|---|
| `domain/*Test` | state machine, inventory invariants, refund balance, gateway tokens |
| `OrderWorkflowIT` | full lifecycle incl. async Kafka refund, declines, cancel, expiry, RBAC/ownership, idempotency, validation |
| `ConcurrencyIT` | 20 buyers / 5 units never oversell; same key x10 = 1 order; concurrent pay charges once; refunds can't exceed payment |
| `TransactionIT` | failed multi-line order rolls back order, reservation, audit, outbox; optimistic-lock lost update; DB constraints |

## Docs

- [Architecture](docs/ARCHITECTURE.md) - design decisions and trade-offs
- [Domain model](docs/DOMAIN_MODEL.md), [Order lifecycle](docs/ORDER_LIFECYCLE.md)
- [API guide](docs/API.md), [Setup & configuration](docs/SETUP.md)

## Configuration

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET` (>= 32 chars) are required outside the
`dev` profile; the app refuses to start without a strong secret.
