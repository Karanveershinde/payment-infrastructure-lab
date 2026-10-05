package com.payflow.payment.service;

import com.payflow.common.domain.enums.IdempotencyStatus;
import com.payflow.common.exception.IdempotencyConflictException;
import com.payflow.common.exception.IdempotencyPayloadMismatchException;
import com.payflow.common.util.HashUtil;
import com.payflow.payment.domain.entity.IdempotencyKeyEntity;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.repository.IdempotencyKeyRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Robust Two-Tier Idempotency Management Engine.
 *
 * Combines Redis distributed locks for low-latency concurrency fencing with
 * PostgreSQL ACID records as the durable source of truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration KEY_EXPIRY_DURATION = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final MetricsService metricsService;

    @Data
    @Builder
    public static class IdempotencyRecord {
        private boolean replay;
        private String responseBody;
        private Integer responseStatusCode;
    }

    /**
     * Attempts to acquire execution rights for the given idempotency key.
     * If completed previously with identical payload, returns cached replay response.
     * If in-flight by a concurrent request, throws IdempotencyConflictException.
     * If payload mismatch occurs, throws IdempotencyPayloadMismatchException.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord checkAndReserve(String merchantId, String idempotencyKey, String requestPayload) {
        String requestHash = HashUtil.sha256Hex(requestPayload);
        String lockKey = String.format("lock:idempotency:%s:%s", merchantId, idempotencyKey);

        // 1. Acquire distributed lock to fence concurrent threads
        boolean lockAcquired = acquireLock(lockKey);
        if (!lockAcquired) {
            log.warn("Concurrent lock contention on Idempotency-Key [{}] for merchant [{}]", idempotencyKey, merchantId);
            metricsService.incrementIdempotencyConflict(merchantId);
            throw new IdempotencyConflictException(idempotencyKey);
        }

        try {
            // 2. Query persistent database ledger
            Optional<IdempotencyKeyEntity> existingOpt = idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(merchantId, idempotencyKey);

            if (existingOpt.isPresent()) {
                IdempotencyKeyEntity entity = existingOpt.get();

                if (entity.getStatus() == IdempotencyStatus.PROCESSING) {
                    // Previous request still processing
                    log.warn("Idempotency key [{}] currently in PROCESSING status", idempotencyKey);
                    metricsService.incrementIdempotencyConflict(merchantId);
                    throw new IdempotencyConflictException(idempotencyKey);
                }

                if (entity.getStatus() == IdempotencyStatus.COMPLETED) {
                    // Verify request payload fingerprint
                    if (!entity.getRequestHash().equals(requestHash)) {
                        log.warn("Idempotency key [{}] payload mismatch. Expected hash {}, got {}",
                                idempotencyKey, entity.getRequestHash(), requestHash);
                        throw new IdempotencyPayloadMismatchException(idempotencyKey);
                    }

                    log.info("Idempotent replay served for key [{}] merchant [{}]", idempotencyKey, merchantId);
                    releaseLock(lockKey);
                    return IdempotencyRecord.builder()
                            .replay(true)
                            .responseBody(entity.getResponseBody())
                            .responseStatusCode(entity.getResponseStatusCode())
                            .build();
                }

                // If previous attempt failed, allow retry by updating status back to PROCESSING
                entity.setStatus(IdempotencyStatus.PROCESSING);
                entity.setRequestHash(requestHash);
                entity.setExpiresAt(Instant.now().plus(KEY_EXPIRY_DURATION));
                idempotencyKeyRepository.save(entity);
            } else {
                // New transaction: create initial ledger record in PROCESSING state
                IdempotencyKeyEntity newKey = IdempotencyKeyEntity.builder()
                        .id(new IdempotencyKeyEntity.IdempotencyKeyId(merchantId, idempotencyKey))
                        .requestHash(requestHash)
                        .status(IdempotencyStatus.PROCESSING)
                        .expiresAt(Instant.now().plus(KEY_EXPIRY_DURATION))
                        .build();
                idempotencyKeyRepository.save(newKey);
            }

            return IdempotencyRecord.builder()
                    .replay(false)
                    .build();

        } catch (Exception e) {
            // Release lock if checkAndReserve fails before transaction starts
            releaseLock(lockKey);
            throw e;
        }
    }

    /**
     * Marks the idempotency key as COMPLETED and persists the final response body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String merchantId, String idempotencyKey, String responseBody, int statusCode) {
        String lockKey = String.format("lock:idempotency:%s:%s", merchantId, idempotencyKey);
        try {
            idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(merchantId, idempotencyKey)
                    .ifPresent(entity -> {
                        entity.setStatus(IdempotencyStatus.COMPLETED);
                        entity.setResponseBody(responseBody);
                        entity.setResponseStatusCode(statusCode);
                        entity.setExpiresAt(Instant.now().plus(KEY_EXPIRY_DURATION));
                        idempotencyKeyRepository.save(entity);
                    });
        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * Marks the idempotency key as FAILED upon unrecoverable execution errors.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String merchantId, String idempotencyKey) {
        String lockKey = String.format("lock:idempotency:%s:%s", merchantId, idempotencyKey);
        try {
            idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(merchantId, idempotencyKey)
                    .ifPresent(entity -> {
                        entity.setStatus(IdempotencyStatus.FAILED);
                        idempotencyKeyRepository.save(entity);
                    });
        } finally {
            releaseLock(lockKey);
        }
    }

    private boolean acquireLock(String lockKey) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", LOCK_TIMEOUT);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis unavailable during lock acquire for key [{}]. Permitting DB constraint fallback: {}", lockKey, e.getMessage());
            return true; // DB Unique constraint will guard concurrency if Redis fails
        }
    }

    public void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Failed to release Redis lock [{}]: {}", lockKey, e.getMessage());
        }
    }
}
