# Order lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: create (stock reserved)
    PENDING --> PAID: payment approved
    PENDING --> PENDING: payment declined (retry allowed)
    PENDING --> CANCELLED: customer cancel / reservation expiry (stock released)
    PAID --> SHIPPED: ship (stock consumed)
    PAID --> CANCELLED: cancel (stock released, auto full refund queued)
    PAID --> REFUNDED: full refund completed (stock released)
    SHIPPED --> DELIVERED: deliver
    SHIPPED --> REFUNDED: full refund completed
    DELIVERED --> REFUNDED: full refund completed
    CANCELLED --> [*]
    REFUNDED --> [*]
```

Partial refunds do not change the order status. The transition table lives in `OrderStatus`; all changes go through
`CustomerOrder.transitionTo`, which returns `409 INVALID_STATE_TRANSITION` for illegal moves.

## Refund workflow

```mermaid
sequenceDiagram
    participant S as Staff
    participant API
    participant DB as PostgreSQL
    participant K as Kafka
    participant C as Refund consumer
    participant G as Gateway
    S->>API: POST /orders/{id}/refunds
    API->>DB: refund PENDING + payment.refund_committed + outbox row (one tx)
    API-->>S: 202 Accepted
    DB->>K: outbox relay publishes REFUND_REQUESTED
    K->>C: deliver (at least once)
    C->>G: refund
    C->>DB: refund COMPLETED/FAILED, order REFUNDED if fully refunded, audit, outbox
```
