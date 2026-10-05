package com.payflow.payment.service;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.request.RefundPaymentRequest;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.payment.PaymentServiceApplication;
import com.payflow.payment.domain.entity.MerchantEntity;
import com.payflow.payment.domain.entity.PaymentEntity;
import com.payflow.payment.provider.PaymentProviderClient;
import com.payflow.payment.repository.MerchantRepository;
import com.payflow.payment.repository.PaymentRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
class ConcurrentRefundTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

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

    private static final String MERCHANT_ID = "mer_refund_concurrency_001";
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
                    .apiKeyHash("hash_refund_test")
                    .name("Refund Test Merchant")
                    .rateLimitPerMinute(1000)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();
            merchantRepository.save(merchant);
        }

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenAnswer(inv ->
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .providerTransactionId("txn_auth_" + UUID.randomUUID())
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        when(providerClient.refund(eq(ProviderType.PROVIDER_A), any())).thenAnswer(inv -> {
            Thread.sleep(10); // Small latency to simulate external provider
            return ProviderModels.ProviderResponse.builder()
                    .success(true)
                    .provider("PROVIDER_A")
                    .providerTransactionId("ref_txn_" + UUID.randomUUID())
                    .status(PaymentStatus.REFUNDED)
                    .build();
        });
    }

    @Test
    @DisplayName("Concurrent Partial Refunds: Total refunded amount must never exceed captured amount")
    void concurrentRefunds_neverExceedCapturedAmount() throws InterruptedException {
        // 1. Create a captured payment of $100.00
        CreatePaymentRequest createReq = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .customerEmail("refund_concurrency@example.com")
                .build();

        PaymentResponse payment = paymentService.createPayment(
                MERCHANT_ID,
                "idem_pay_refund_concurrency_" + UUID.randomUUID(),
                createReq,
                "{\"amount\":100.00}",
                "corr_refund_test"
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        String paymentId = payment.getPaymentId();

        // 2. Launch 5 concurrent threads, each attempting a $30.00 partial refund.
        // Total attempted: 5 x $30.00 = $150.00 on a $100.00 payment.
        // Exactly 3 refunds should succeed ($90.00 total) and 2 must fail due to balance check.
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            tasks.add(() -> {
                startLatch.await();
                try {
                    paymentService.refundPayment(
                            MERCHANT_ID,
                            paymentId,
                            new RefundPaymentRequest(BigDecimal.valueOf(30.00), "Partial refund attempt " + index),
                            "idem_refund_" + index,
                            "corr_ref_" + index
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
                return null;
            });
        }

        for (Callable<Void> task : tasks) {
            executor.submit(task);
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // 3. Verify invariants
        PaymentEntity updatedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(successCount.get()).isEqualTo(3);
        assertThat(rejectedCount.get()).isEqualTo(2);
        assertThat(updatedPayment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updatedPayment.getRefundedAmount()).isLessThanOrEqualTo(updatedPayment.getAmount());
    }
}
