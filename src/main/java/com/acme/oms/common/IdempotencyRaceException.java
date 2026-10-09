package com.acme.oms.common;

/** Two requests with the same Idempotency-Key raced; the loser rolls back and is retried as a replay. */
public class IdempotencyRaceException extends RuntimeException {
    public IdempotencyRaceException() {
        super("Concurrent request with the same Idempotency-Key");
    }
}
