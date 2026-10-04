package com.payflow.payment.service;

import com.payflow.common.domain.enums.IdempotencyStatus;
import com.payflow.common.exception.IdempotencyConflictException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyConcurrencyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private MetricsService metricsService;

    private IdempotencyService idempotencyService;

    private static final String MERCHANT_ID = "mer_concurrent_001";
    private static final String IDEMP_KEY = "idem_concurrent_key_999";
    private static final String PAYLOAD = "{\"amount\":100.00,\"currency\":\"USD\"}";
    private static final String LOCK_KEY = "lock:idempotency:" + MERCHANT_ID + ":" + IDEMP_KEY;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, idempotencyKeyRepository, metricsService);
    }

    @Test
    @DisplayName("Concurrency: 20 simultaneous threads on identical Idempotency-Key must serialize safely")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicBoolean redisLockHolder = new AtomicBoolean(false);
        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger conflictsDetected = new AtomicInteger(0);
        ConcurrentHashMap<String, IdempotencyKeyEntity> mockDb = new ConcurrentHashMap<>();

        // Atomic simulation of Redis setIfAbsent
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("LOCKED"), any(Duration.class)))
                .thenAnswer(inv -> redisLockHolder.compareAndSet(false, true));

        // Mock DB repository
        lenient().when(idempotencyKeyRepository.findById_MerchantIdAndId_IdempotencyKey(MERCHANT_ID, IDEMP_KEY))
                .thenAnswer(inv -> Optional.ofNullable(mockDb.get(IDEMP_KEY)));

        lenient().when(idempotencyKeyRepository.save(any(IdempotencyKeyEntity.class)))
                .thenAnswer(inv -> {
                    IdempotencyKeyEntity e = inv.getArgument(0);
                    mockDb.put(IDEMP_KEY, e);
                    return e;
                });

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to blast simultaneously
                    IdempotencyService.IdempotencyRecord record = idempotencyService.checkAndReserve(MERCHANT_ID, IDEMP_KEY, PAYLOAD);
                    if (!record.isReplay()) {
                        successfulReservations.incrementAndGet();
                    }
                } catch (IdempotencyConflictException e) {
                    conflictsDetected.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Fire all threads at the exact same instant!
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Exactly 1 thread must acquire the reservation and execute
        assertThat(successfulReservations.get()).isEqualTo(1);
        // All other 19 threads must be safely rejected with 409 Conflict
        assertThat(conflictsDetected.get()).isEqualTo(threadCount - 1);
    }
}
