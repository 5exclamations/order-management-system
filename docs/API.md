# API guide

Interactive docs: `/swagger-ui.html`; spec: `/v3/api-docs`. Errors are RFC 7807 `application/problem+json` with a stable
`code` (e.g. `INSUFFICIENT_STOCK`, `INVALID_STATE_TRANSITION`, `VALIDATION_FAILED`) and `requestId`.

| Area | Endpoint | Roles |
|---|---|---|
| Auth | `POST /api/v1/auth/register`, `/login` | public |
| Customers | `POST/GET /customers`, `GET /customers/me`, `GET/PUT /customers/{id}`, `POST /customers/{id}/(de)activate` | staff; customer: self |
| Catalog | `POST /products`, `PUT/DELETE /products/{id}` | staff |
| | `GET /products`, `GET /products/{id}` | any authenticated |
| Inventory | `GET /inventory[/{productId}]`, `POST /inventory/{productId}/adjustments` | staff |
| Orders | `POST /orders` (Idempotency-Key), `GET /orders[/{id}]`, `POST /orders/{id}/cancel` | owner or staff |
| | `POST /orders/{id}/ship`, `/deliver` | staff |
| Payments | `POST /orders/{id}/payments` (Idempotency-Key), `GET .../payments` | owner or staff |
| Refunds | `POST /orders/{id}/refunds` (Idempotency-Key, 202), `GET .../refunds` | staff / owner (read) |
| Audit | `GET /audit-logs?entityType=&entityId=` | admin |

STAFF = ADMIN or STAFF role. Customers get 404 (not 403) for other customers' orders.

Idempotency-Key: same key + same body returns the original result (200); same key + different body is `422 IDEMPOTENCY_KEY_REUSED`.
Payment test tokens: `tok_visa`, `tok_mastercard` (approve), `tok_declined`, `tok_insufficient_funds` (decline),
`tok_refund_fails` (approve, refund later fails). A decline returns 201 with `status: FAILED`; the order stays PENDING.
