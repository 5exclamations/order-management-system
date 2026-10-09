# Setup

Requirements: JDK 21, Maven 3.9+, Docker.

1. `docker compose up -d` - PostgreSQL 16 (host port 5433) and Kafka 3.9 KRaft (host port 29092).
2. `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`. Flyway creates the schema on boot; Hibernate only validates it.
3. Open `/swagger-ui.html`, log in via `POST /api/v1/auth/login`, click Authorize and paste the token.

| Variable | Purpose | Default (dev only) |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL | localhost:5433 / oms / oms |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka | localhost:29092 |
| `JWT_SECRET` | HS256 key, >= 32 chars | dev placeholder |
| `DEV_SEED_PASSWORD` | demo admin/staff password | `DevPassw0rd!` |
| `app.orders.reservation-ttl` | unpaid order expiry | 15m |

Logs are structured JSON (logstash format) with `requestId` from the `X-Request-Id` header.
Kafka topic `oms.events` (3 partitions, key = order id); failed messages go to `oms.events.DLT`.

Troubleshooting: `exec format error` from a Kafka container means a broken image build for your CPU - use `apache/kafka:3.9.1`.
