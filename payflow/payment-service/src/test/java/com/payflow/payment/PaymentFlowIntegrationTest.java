package com.payflow.payment;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CapturePaymentRequest;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.request.RefundPaymentRequest;
import com.payflow.common.dto.response.CaptureResponse;
import com.payflow.common.dto.response.PaymentEventResponse;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.common.dto.response.RefundResponse;
import com.payflow.common.exception.InvalidStateTransitionException;
import com.payflow.common.exception.PaymentValidationException;
import com.payflow.common.exception.ProviderUnavailableException;
import com.payflow.payment.domain.entity.MerchantEntity;
import com.payflow.payment.domain.entity.PaymentEntity;
import com.payflow.payment.provider.PaymentProviderClient;
import com.payflow.payment.repository.MerchantRepository;
import com.payflow.payment.repository.PaymentRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

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

    private static final String MERCHANT_ID = "mer_integration_001";
    private static final String API_KEY_HASH = "mock_hash_integration";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());

        // Seed test merchant
        if (merchantRepository.findById(MERCHANT_ID).isEmpty()) {
            MerchantEntity merchant = MerchantEntity.builder()
                    .id(MERCHANT_ID)
                    .apiKeyHash(API_KEY_HASH)
                    .name("Integration Test Merchant")
                    .rateLimitPerMinute(1000)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();
            merchantRepository.save(merchant);
        }
    }

    @Test
    @DisplayName("E2E Test 1: Full Payment Creation -> Auto-Capture Flow")
    void shouldExecuteFullPaymentCreationFlow() {
        String idempKey = "idem_e2e_" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(199.99))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .customerEmail("alice@example.com")
                .build();

        ProviderModels.ProviderResponse authResponse = ProviderModels.ProviderResponse.builder()
                .success(true)
                .provider("PROVIDER_A")
                .providerTransactionId("txn_prov_a_001")
                .status(PaymentStatus.AUTHORIZED)
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(authResponse);

        PaymentResponse response = paymentService.createPayment(
                MERCHANT_ID,
                idempKey,
                request,
                "{\"amount\":199.99}",
                "corr_e2e_1"
        );

        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).startsWith("pay_");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(199.99));
        assertThat(response.getProvider()).isEqualTo(ProviderType.PROVIDER_A);

        // Verify DB persistence
        PaymentEntity persisted = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(persisted.getProviderTransactionId()).isEqualTo("txn_prov_a_001");

        // Verify audit trail transitions: CREATED -> PROCESSING -> AUTHORIZED -> CAPTURED
        List<PaymentEventResponse> events = paymentService.getPaymentEvents(MERCHANT_ID, response.getPaymentId());
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getToState()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(events.get(1).getToState()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(events.get(2).getToState()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    @DisplayName("E2E Test 2: Idempotency Replay returns exact cached response without double charge")
    void shouldReturnCachedIdempotencyReplay() {
        String idempKey = "idem_replay_" + UUID.randomUUID();
        String rawJson = "{\"amount\":75.00,\"currency\":\"USD\"}";

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(75.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .providerTransactionId("txn_replay_123")
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        // First call: executes payment
        PaymentResponse firstResponse = paymentService.createPayment(MERCHANT_ID, idempKey, request, rawJson, "corr_1");

        // Second call with same idempotency key and payload: returns replay from cache
        PaymentResponse replayResponse = paymentService.createPayment(MERCHANT_ID, idempKey, request, rawJson, "corr_2");

        assertThat(replayResponse.getPaymentId()).isEqualTo(firstResponse.getPaymentId());
        assertThat(replayResponse.getStatus()).isEqualTo(firstResponse.getStatus());
        assertThat(replayResponse.getProviderTransactionId()).isEqualTo(firstResponse.getProviderTransactionId());
    }

    @Test
    @DisplayName("E2E Test 3: Two-step Manual Authorization & Capture")
    void shouldAuthorizeThenCaptureManually() {
        String idempKey = "idem_manual_" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(300.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("MANUAL")
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .providerTransactionId("txn_manual_auth")
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        PaymentResponse authResponse = paymentService.createPayment(MERCHANT_ID, idempKey, request, "{\"amount\":300.00}", "corr_auth");
        assertThat(authResponse.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

        // Now manual capture
        when(providerClient.capture(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .status(PaymentStatus.CAPTURED)
                        .build()
        );

        CaptureResponse captureResponse = paymentService.capturePayment(
                MERCHANT_ID,
                authResponse.getPaymentId(),
                new CapturePaymentRequest(BigDecimal.valueOf(300.00), "Settle authorized order"),
                "idemp_cap_1",
                "corr_cap"
        );

        assertThat(captureResponse.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        PaymentEntity capturedPayment = paymentRepository.findById(authResponse.getPaymentId()).orElseThrow();
        assertThat(capturedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    @DisplayName("E2E Test 4: Refund Flow on Captured Payment")
    void shouldRefundCapturedPayment() {
        String idempKey = "idem_refund_" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .providerTransactionId("txn_refund_target")
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        PaymentResponse payment = paymentService.createPayment(MERCHANT_ID, idempKey, request, "{\"amount\":100.00}", "corr_ref_init");

        // Perform partial refund of $40.00
        when(providerClient.refund(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .providerTransactionId("ref_prov_123")
                        .status(PaymentStatus.REFUNDED)
                        .build()
        );

        RefundResponse refundResponse = paymentService.refundPayment(
                MERCHANT_ID,
                payment.getPaymentId(),
                new RefundPaymentRequest(BigDecimal.valueOf(40.00), "Item returned"),
                "idemp_ref_1",
                "corr_ref"
        );

        assertThat(refundResponse.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
        assertThat(refundResponse.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);

        PaymentEntity updated = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
        assertThat(updated.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
    }

    @Test
    @DisplayName("E2E Test 5: Resilience Failover from ProviderA (503 Outage) to ProviderB")
    void shouldFailoverToProviderBDuringProviderAOutage() {
        String idempKey = "idem_failover_e2e_" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(50.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .build();

        // ProviderA is down
        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any()))
                .thenThrow(new ProviderUnavailableException("PROVIDER_A", "503 Outage"));

        // ProviderB is operational
        when(providerClient.authorize(eq(ProviderType.PROVIDER_B), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_B")
                        .providerTransactionId("txn_prov_b_backup")
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        PaymentResponse response = paymentService.createPayment(
                MERCHANT_ID,
                idempKey,
                request,
                "{\"amount\":50.00}",
                "corr_failover"
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.getProvider()).isEqualTo(ProviderType.PROVIDER_B);
        assertThat(response.getProviderTransactionId()).isEqualTo("txn_prov_b_backup");
    }

    @Test
    @DisplayName("E2E Test 6: State Machine prevents illegal operations (e.g. refunding greater than amount)")
    void shouldRejectIllegalRefundExceedingBalance() {
        String idempKey = "idem_excess_" + UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(50.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(
                ProviderModels.ProviderResponse.builder()
                        .success(true)
                        .provider("PROVIDER_A")
                        .providerTransactionId("txn_excess")
                        .status(PaymentStatus.AUTHORIZED)
                        .build()
        );

        PaymentResponse payment = paymentService.createPayment(MERCHANT_ID, idempKey, request, "{\"amount\":50.00}", "corr_excess");

        // Attempt to refund $100.00 on a $50.00 payment
        assertThatThrownBy(() -> paymentService.refundPayment(
                MERCHANT_ID,
                payment.getPaymentId(),
                new RefundPaymentRequest(BigDecimal.valueOf(100.00), "Illegal excess refund"),
                "idemp_excess_ref",
                "corr_err"
        )).isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("exceeds remaining refundable balance");
    }
}
