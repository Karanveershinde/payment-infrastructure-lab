package com.payflow.webhook.service;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.domain.enums.WebhookEventStatus;
import com.payflow.common.dto.request.WebhookPayload;
import com.payflow.common.exception.InvalidWebhookSignatureException;
import com.payflow.common.util.HmacSignatureUtil;
import com.payflow.common.util.JsonUtil;
import com.payflow.webhook.domain.entity.WebhookEventEntity;
import com.payflow.webhook.repository.WebhookEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookProcessorServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    private WebhookProcessorService webhookProcessorService;

    private static final String SECRET = "whsec_test_secret_key_1234567890";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        webhookProcessorService = new WebhookProcessorService(webhookEventRepository, redisTemplate, kafkaTemplate, meterRegistry);
        ReflectionTestUtils.setField(webhookProcessorService, "webhookSecret", SECRET);
    }

    @Test
    @DisplayName("Should successfully process valid HMAC signed webhook")
    void shouldProcessValidWebhook() {
        WebhookPayload payload = WebhookPayload.builder()
                .provider(ProviderType.PROVIDER_A)
                .providerEventId("evt_valid_123")
                .eventType("payment.capture.success")
                .paymentId("pay_12345")
                .status(PaymentStatus.CAPTURED)
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .timestamp(Instant.now())
                .build();

        String rawJson = JsonUtil.toJson(payload);
        String signature = HmacSignatureUtil.calculateSignature(SECRET, rawJson);

        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(webhookEventRepository.findByProviderAndProviderEventId("PROVIDER_A", "evt_valid_123")).thenReturn(Optional.empty());

        WebhookProcessorService.WebhookResult result = webhookProcessorService.processWebhook("PROVIDER_A", rawJson, signature);

        assertThat(result.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        verify(webhookEventRepository, atLeastOnce()).save(any(WebhookEventEntity.class));
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature")
    void shouldRejectInvalidSignature() {
        WebhookPayload payload = WebhookPayload.builder()
                .provider(ProviderType.PROVIDER_A)
                .providerEventId("evt_bad_sig")
                .build();

        String rawJson = JsonUtil.toJson(payload);

        assertThatThrownBy(() -> webhookProcessorService.processWebhook("PROVIDER_A", rawJson, "invalid_signature_hex"))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName("Should detect and deduplicate duplicate webhook via Redis")
    void shouldDeduplicateViaRedis() {
        WebhookPayload payload = WebhookPayload.builder()
                .provider(ProviderType.PROVIDER_A)
                .providerEventId("evt_duplicate_redis")
                .build();

        String rawJson = JsonUtil.toJson(payload);
        String signature = HmacSignatureUtil.calculateSignature(SECRET, rawJson);

        // Redis indicates key already exists
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        WebhookProcessorService.WebhookResult result = webhookProcessorService.processWebhook("PROVIDER_A", rawJson, signature);

        assertThat(result.getStatus()).isEqualTo(WebhookEventStatus.DUPLICATE);
        assertThat(result.getMessage()).contains("Duplicate webhook event ignored");
    }
}
