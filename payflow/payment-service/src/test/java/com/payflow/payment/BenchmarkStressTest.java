package com.payflow.payment;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.payment.domain.entity.MerchantEntity;
import com.payflow.payment.provider.PaymentProviderClient;
import com.payflow.payment.repository.MerchantRepository;
import com.payflow.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
class BenchmarkStressTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @MockBean
    private PaymentProviderClient providerClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String MERCHANT_ID = "mer_bench_001";
    private final ConcurrentHashMap<String, String> mockRedisStorage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return mockRedisStorage.putIfAbsent(key, "LOCKED") == null;
        });
        when(redisTemplate.delete(any(String.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            mockRedisStorage.remove(key);
            return true;
        });
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());

        if (merchantRepository.findById(MERCHANT_ID).isEmpty()) {
            MerchantEntity merchant = MerchantEntity.builder()
                    .id(MERCHANT_ID)
                    .apiKeyHash("bench_hash")
                    .name("Benchmark Merchant")
                    .rateLimitPerMinute(10000)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();
            merchantRepository.save(merchant);
        }

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenAnswer(inv -> {
            Thread.sleep(5); // 5ms simulated network latency
            return ProviderModels.ProviderResponse.builder()
                    .success(true)
                    .provider("PROVIDER_A")
                    .providerTransactionId("txn_bench_" + UUID.randomUUID())
                    .status(PaymentStatus.AUTHORIZED)
                    .build();
        });
    }

    @Test
    @DisplayName("Local Load Benchmark: 200 Concurrent Transactions Throughput & Latency Profiling")
    void executeLocalBenchmark() throws InterruptedException {
        int totalRequests = 200;
        int concurrencyLevel = 20;

        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        long overallStartTime = System.currentTimeMillis();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            tasks.add(() -> {
                startLatch.await();
                long start = System.currentTimeMillis();
                try {
                    String idempKey = "bench_key_" + index + "_" + UUID.randomUUID();
                    CreatePaymentRequest req = CreatePaymentRequest.builder()
                            .amount(BigDecimal.valueOf(100.00 + (index % 50)))
                            .currency(Currency.USD)
                            .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                            .paymentMethodToken("tok_visa_success")
                            .captureMethod("AUTOMATIC")
                            .customerEmail("bench" + index + "@example.com")
                            .build();

                    PaymentResponse resp = paymentService.createPayment(
                            MERCHANT_ID,
                            idempKey,
                            req,
                            "{\"idx\":" + index + "}",
                            "corr_bench_" + index
                    );

                    if (resp.getStatus() == PaymentStatus.CAPTURED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Benchmark error: " + e.getMessage());
                } finally {
                    long duration = System.currentTimeMillis() - start;
                    latenciesMs.add(duration);
                    finishLatch.countDown();
                }
                return null;
            });
        }

        for (Callable<Void> task : tasks) {
            executor.submit(task);
        }

        startLatch.countDown(); // Start benchmark
        finishLatch.await();
        long totalExecutionTimeMs = System.currentTimeMillis() - overallStartTime;
        executor.shutdown();

        // Calculate statistics
        Collections.sort(latenciesMs);
        long p50 = latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99 = latenciesMs.get((int) (latenciesMs.size() * 0.99));
        double tps = (totalRequests * 1000.0) / totalExecutionTimeMs;

        System.out.println("=================================================");
        System.out.println(" PAYFLOW LOCAL BENCHMARK RESULTS");
        System.out.println("=================================================");
        System.out.printf(" Total Requests Processed: %d%n", totalRequests);
        System.out.printf(" Successful Transactions:  %d (%.1f%%)%n", successCount.get(), (successCount.get() * 100.0 / totalRequests));
        System.out.printf(" Concurrency Level:        %d workers%n", concurrencyLevel);
        System.out.printf(" Total Duration:           %d ms%n", totalExecutionTimeMs);
        System.out.printf(" Measured Throughput:      %.2f TPS%n", tps);
        System.out.printf(" Latency (p50):            %d ms%n", p50);
        System.out.printf(" Latency (p95):            %d ms%n", p95);
        System.out.printf(" Latency (p99):            %d ms%n", p99);
        System.out.println("=================================================");

        assertThat(successCount.get()).isEqualTo(totalRequests);
    }
}
