package com.payflow.webhook.service;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.WebhookEventStatus;
import com.payflow.common.domain.statemachine.PaymentStateMachine;
import com.payflow.common.dto.request.WebhookPayload;
import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.events.PaymentEventTopics;
import com.payflow.common.exception.InvalidWebhookSignatureException;
import com.payflow.common.util.HmacSignatureUtil;
import com.payflow.common.util.JsonUtil;
import com.payflow.webhook.domain.entity.WebhookEventEntity;
import com.payflow.webhook.repository.WebhookEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessorService {

    private final WebhookEventRepository webhookEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${webhook.secret:whsec_test_secret_key_1234567890}")
    private String webhookSecret;

    @Data
    @Builder
    public static class WebhookResult {
        private String eventId;
        private WebhookEventStatus status;
        private String message;
    }

    @Transactional
    public WebhookResult processWebhook(String provider, String rawPayload, String signature) {
        String normalizedProvider = provider.toUpperCase();

        // 1. Verify HMAC Signature
        if (signature == null || !HmacSignatureUtil.verifySignature(webhookSecret, rawPayload, signature)) {
            log.warn("Invalid HMAC webhook signature from provider [{}]", normalizedProvider);
            throw new InvalidWebhookSignatureException("Invalid webhook signature for provider: " + normalizedProvider);
        }

        // 2. Parse payload
        WebhookPayload payload = JsonUtil.fromJson(rawPayload, WebhookPayload.class);
        String providerEventId = (payload != null && payload.getProviderEventId() != null && !payload.getProviderEventId().isBlank())
                ? payload.getProviderEventId()
                : UUID.randomUUID().toString();

        // 3. Fast Redis Deduplication
        String redisDedupeKey = String.format("seen:webhook:%s:%s", normalizedProvider, providerEventId);
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisDedupeKey, "PROCESSED", Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Duplicate webhook event detected via Redis for provider [{}] event [{}]", normalizedProvider, providerEventId);
                recordDuplicateMetric(normalizedProvider);
                return WebhookResult.builder()
                        .eventId(providerEventId)
                        .status(WebhookEventStatus.DUPLICATE)
                        .message("Duplicate webhook event ignored")
                        .build();
            }
        } catch (Exception e) {
            log.warn("Redis deduplication cache unavailable: {}", e.getMessage());
        }

        // 4. Persistent Database Deduplication
        Optional<WebhookEventEntity> existing = webhookEventRepository.findByProviderAndProviderEventId(normalizedProvider, providerEventId);
        if (existing.isPresent()) {
            log.info("Duplicate webhook event detected via DB for provider [{}] event [{}]", normalizedProvider, providerEventId);
            recordDuplicateMetric(normalizedProvider);
            return WebhookResult.builder()
                    .eventId(providerEventId)
                    .status(WebhookEventStatus.DUPLICATE)
                    .message("Duplicate webhook event already recorded")
                    .build();
        }

        // 5. Persist Webhook Event
        String internalEventId = UUID.randomUUID().toString();
        WebhookEventEntity entity = WebhookEventEntity.builder()
                .id(internalEventId)
                .provider(normalizedProvider)
                .providerEventId(providerEventId)
                .eventType(payload.getEventType())
                .paymentId(payload.getPaymentId())
                .payload(rawPayload)
                .signature(signature)
                .status(WebhookEventStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build();

        webhookEventRepository.save(entity);

        // 6. State Machine Validation & Event Forwarding
        WebhookEventStatus finalStatus = WebhookEventStatus.PROCESSED;
        String processingMessage = "Webhook processed successfully";

        // Check if event targets a payment state transition
        if (payload.getStatus() != null && payload.getStatus().isTerminal()) {
            log.info("Terminal state webhook received for payment [{}]: {}", payload.getPaymentId(), payload.getStatus());
        }

        // Publish to Kafka
        publishWebhookKafkaEvents(normalizedProvider, payload, internalEventId);

        // 7. Mark event as processed
        entity.setStatus(finalStatus);
        entity.setProcessedAt(Instant.now());
        webhookEventRepository.save(entity);

        return WebhookResult.builder()
                .eventId(providerEventId)
                .status(finalStatus)
                .message(processingMessage)
                .build();
    }

    private void publishWebhookKafkaEvents(String provider, WebhookPayload payload, String eventId) {
        try {
            PaymentEventPayload eventPayload = PaymentEventPayload.builder()
                    .paymentId(payload.getPaymentId())
                    .status(payload.getStatus())
                    .amount(payload.getAmount())
                    .currency(payload.getCurrency())
                    .provider(payload.getProvider())
                    .providerTransactionId(payload.getProviderTransactionId())
                    .failureCode(payload.getErrorCode())
                    .failureMessage(payload.getErrorMessage())
                    .timestamp(Instant.now())
                    .build();

            PaymentEvent<PaymentEventPayload> event = PaymentEvent.of(
                    PaymentEventTopics.PAYMENT_WEBHOOK_RECEIVED,
                    payload.getPaymentId() != null ? payload.getPaymentId() : "N/A",
                    "mer_webhook",
                    eventId,
                    eventPayload
            );

            String key = payload.getPaymentId() != null ? payload.getPaymentId() : eventId;
            kafkaTemplate.send(PaymentEventTopics.PAYMENT_WEBHOOK_RECEIVED, key, JsonUtil.toJson(event));
            log.info("Published webhook event [{}] to Kafka", eventId);
        } catch (Exception e) {
            log.warn("Failed to publish webhook event to Kafka: {}", e.getMessage());
        }
    }

    private void recordDuplicateMetric(String provider) {
        try {
            meterRegistry.counter("webhook_duplicate_total", "provider", provider).increment();
        } catch (Exception ignored) {}
    }
}
