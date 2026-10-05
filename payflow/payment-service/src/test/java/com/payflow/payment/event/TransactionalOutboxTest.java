package com.payflow.payment.event;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.payment.PaymentServiceApplication;
import com.payflow.payment.domain.entity.MerchantEntity;
import com.payflow.payment.domain.entity.OutboxEventEntity;
import com.payflow.payment.provider.PaymentProviderClient;
import com.payflow.payment.repository.MerchantRepository;
import com.payflow.payment.repository.OutboxEventRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
class TransactionalOutboxTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherWorker outboxPublisherWorker;

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

    private static final String MERCHANT_ID = "mer_outbox_test_001";
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
                    .apiKeyHash("hash_outbox_test")
                    .name("Outbox Test Merchant")
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
                        .providerTransactionId("txn_outbox_" + UUID.randomUUID())
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );
    }

    @Test
    @DisplayName("Transactional Outbox: State transitions create outbox records atomically in DB")
    void stateTransitions_createOutboxRecordsAtomically() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(75.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .customerEmail("outbox@example.com")
                .build();

        PaymentResponse response = paymentService.createPayment(
                MERCHANT_ID,
                "idem_outbox_" + UUID.randomUUID(),
                request,
                "{\"amount\":75.00}",
                "corr_outbox_1"
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        List<OutboxEventEntity> outboxEvents = outboxEventRepository.findByAggregateId(response.getPaymentId());
        assertThat(outboxEvents).isNotEmpty();
        // Payment went from CREATED -> PROCESSING -> AUTHORIZED -> CAPTURED (3 transitions = 3 outbox events)
        assertThat(outboxEvents.size()).isGreaterThanOrEqualTo(3);

        for (OutboxEventEntity event : outboxEvents) {
            assertThat(event.getAggregateId()).isEqualTo(response.getPaymentId());
            assertThat(event.getPayload()).contains(response.getPaymentId());
            assertThat(event.getStatus()).isIn("PENDING", "PUBLISHED");
        }
    }

    @Test
    @DisplayName("Outbox Worker: Retries failed events and records error message")
    void outboxWorker_retriesAndRecordsError() {
        OutboxEventEntity failedEvent = OutboxEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .aggregateType("PAYMENT")
                .aggregateId("pay_test_retry")
                .topic("payment.test")
                .eventKey("pay_test_retry")
                .payload("{\"test\":\"data\"}")
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        outboxEventRepository.save(failedEvent);

        when(kafkaTemplate.send(eq("payment.test"), any(), any())).thenThrow(new RuntimeException("Kafka Broker Connection Refused"));

        boolean published = outboxPublisherWorker.publishSingleEvent(failedEvent);

        assertThat(published).isFalse();
        OutboxEventEntity updated = outboxEventRepository.findById(failedEvent.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getErrorMessage()).contains("Kafka Broker Connection Refused");
        assertThat(updated.getStatus()).isEqualTo("PENDING");
    }
}
