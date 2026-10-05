package com.payflow.payment.service;

import com.payflow.common.domain.enums.IdempotencyStatus;
import com.payflow.common.exception.IdempotencyConflictException;
import com.payflow.common.exception.IdempotencyPayloadMismatchException;
import com.payflow.common.util.HashUtil;
import com.payflow.payment.domain.entity.IdempotencyKeyEntity;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private MetricsService metricsService;

    private IdempotencyService idempotencyService;

    private static final String MERCHANT_ID = "mer_test_001";
    private static final String IDEMP_KEY = "idem_key_abc123";
    private static final String PAYLOAD = "{\"amount\":100.00,\"currency\":\"USD\"}";
    private static final String LOCK_KEY = "lock:idempotency:mer_test_001:idem_key_abc123";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, idempotencyKeyRepository, metricsService);
    }

    @Test
    @DisplayName("Should successfully reserve new idempotency key")
    void shouldReserveNewKey() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("LOCKED"), any(Duration.class))).thenReturn(true);
        when(idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(MERCHANT_ID, IDEMP_KEY)).thenReturn(Optional.empty());

        IdempotencyService.IdempotencyRecord record = idempotencyService.checkAndReserve(MERCHANT_ID, IDEMP_KEY, PAYLOAD);

        assertThat(record.isReplay()).isFalse();
        verify(idempotencyKeyRepository).save(any(IdempotencyKeyEntity.class));
    }

    @Test
    @DisplayName("Should return cached response on valid duplicate replay")
    void shouldReturnReplayForCompletedKey() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("LOCKED"), any(Duration.class))).thenReturn(true);
        String expectedHash = HashUtil.sha256Hex(PAYLOAD);

        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .id(new IdempotencyKeyEntity.IdempotencyKeyId(MERCHANT_ID, IDEMP_KEY))
                .requestHash(expectedHash)
                .status(IdempotencyStatus.COMPLETED)
                .responseBody("{\"paymentId\":\"pay_123\",\"status\":\"CAPTURED\"}")
                .responseStatusCode(201)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(MERCHANT_ID, IDEMP_KEY)).thenReturn(Optional.of(entity));

        IdempotencyService.IdempotencyRecord record = idempotencyService.checkAndReserve(MERCHANT_ID, IDEMP_KEY, PAYLOAD);

        assertThat(record.isReplay()).isTrue();
        assertThat(record.getResponseBody()).isEqualTo(entity.getResponseBody());
        assertThat(record.getResponseStatusCode()).isEqualTo(201);
        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("Should throw 409 Conflict when concurrent request holds lock")
    void shouldThrowConflictWhenLockHeld() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("LOCKED"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> idempotencyService.checkAndReserve(MERCHANT_ID, IDEMP_KEY, PAYLOAD))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("currently processing");

        verify(metricsService).incrementIdempotencyConflict(MERCHANT_ID);
    }

    @Test
    @DisplayName("Should throw 422 Unprocessable Entity when same key has different payload")
    void shouldThrowMismatchOnDifferentPayload() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("LOCKED"), any(Duration.class))).thenReturn(true);

        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .id(new IdempotencyKeyEntity.IdempotencyKeyId(MERCHANT_ID, IDEMP_KEY))
                .requestHash("different_sha256_hash_value")
                .status(IdempotencyStatus.COMPLETED)
                .responseBody("{\"paymentId\":\"pay_123\"}")
                .responseStatusCode(201)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(MERCHANT_ID, IDEMP_KEY)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> idempotencyService.checkAndReserve(MERCHANT_ID, IDEMP_KEY, PAYLOAD))
                .isInstanceOf(IdempotencyPayloadMismatchException.class)
                .hasMessageContaining("previously used with a different request payload");
    }
}
