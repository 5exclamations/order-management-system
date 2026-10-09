package com.acme.oms.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key support. Keys are scoped per caller. The key row is inserted in the SAME transaction as the
 * work it protects, so either both commit or neither does. A concurrent duplicate blocks on the unique index
 * until the first request commits, then fails with {@link IdempotencyRaceException} and is retried as a replay.
 */
@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 200;

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /** @return id of the resource created by an earlier request with this key, if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> findReplay(String scope, String key, String requestHash, String resourceType) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header is required and must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        return repository.find(scope, key).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash) || !existing.getResourceType().equals(resourceType)) {
                throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request");
            }
            return existing.getResourceId();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String scope, String key, String requestHash, String resourceType, UUID resourceId) {
        try {
            repository.saveAndFlush(new IdempotencyKey(scope, key, requestHash, resourceType, resourceId));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyRaceException();
        }
    }

    public static String hash(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
