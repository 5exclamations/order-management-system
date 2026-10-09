# Architecture

A single deployable Spring Boot service (modular monolith), package-by-feature: `customer`, `catalog`, `inventory`,
`order`, `payment`, `audit`, `event`, `auth`, `security`, `common`. No microservices: the consistency rules below
need one database transaction.

## Key decisions

| Concern | Decision | Why |
|---|---|---|
| Stock correctness | `@Version` on `inventory`; whole use case retried on conflict (`@RetryOnConflict` aspect, outside the transaction) | no oversell without holding row locks; losers re-read fresh data |
| Atomicity | One transaction per use case: order + reservations + audit + outbox | a failed line rolls back everything (`TransactionIT`) |
| Payment | `SELECT ... FOR UPDATE` on the order before charging | charging is irreversible, so serialise before the side effect instead of detecting afterwards |
| Idempotency | key row inserted in the same tx as the work; unique `(scope,key)`; request hash | retries and races never create duplicates |
| Events | transactional outbox -> Kafka (`SKIP LOCKED` relay); consumers idempotent; retry then DLT | no dual-write loss; at-least-once is safe |
| Refunds | request (sync, 202) + settle (async consumer) | gateway latency/failures stay off the request path |
| Refund over-run | `payment.refund_committed` on a versioned row + DB check | concurrent refunds can't exceed the payment |
| Expiry | scheduled job cancels unpaid orders after TTL | unpaid carts can't hold stock forever |
| AuthN/Z | stateless JWT (HS256), `@PreAuthorize` role rules, ownership checks in services | coarse + object-level control |
| Errors | one `@RestControllerAdvice`, problem+json | uniform contract |
| Observability | JSON logs with `requestId` in MDC, audit table, actuator health | traceability |

## Known limitations

- Payment gateway is simulated and called inside the DB transaction (a real PSP call needs an idempotency reference,
  which the `PaymentGateway` port already takes).
- Single currency; refunds do not restock shipped goods.
- HS256 shared secret; use an external IdP / asymmetric keys in production.
- Idempotency keys are never purged; add a TTL cleanup job.
- Outbox relay preserves order per instance only; consumers must tolerate duplicates.
