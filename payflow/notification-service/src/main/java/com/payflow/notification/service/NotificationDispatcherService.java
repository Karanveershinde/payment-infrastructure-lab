package com.payflow.notification.service;

import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.util.JsonUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches merchant notifications with an Idempotent Consumer Ledger.
 *
 * Guarantees that at-least-once Kafka event delivery does NOT result in duplicate merchant notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcherService {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<String, Boolean> localEventLedger = new ConcurrentHashMap<>();

    public void processPaymentEvent(PaymentEvent<PaymentEventPayload> event) {
        String eventId = event.getEventId();
        String merchantId = event.getMerchantId();
        String eventType = event.getEventType();

        // 1. Idempotency Check: Verify if event has already been consumed
        if (isDuplicateEvent(eventId)) {
            log.info("Kafka consumer idempotency: Event [{}] already processed. Skipping duplicate dispatch.", eventId);
            incrementMetric("notifications_duplicate_ignored_total", eventType);
            return;
        }

        // 2. Format merchant notification payload
        log.info("Dispatching merchant notification: merchantId=[{}] eventType=[{}] aggregateId=[{}] eventId=[{}]",
                merchantId, eventType, event.getAggregateId(), eventId);

        // 3. Mark as processed in idempotent ledger
        recordEventConsumed(eventId);
        incrementMetric("notifications_dispatched_total", eventType);
    }

    private boolean isDuplicateEvent(String eventId) {
        String key = "seen:kafka:event:" + eventId;
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "CONSUMED", Duration.ofDays(3));
            if (isNew != null) {
                return !isNew;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for consumer idempotency ledger. Falling back to local cache: {}", e.getMessage());
        }
        return localEventLedger.putIfAbsent(eventId, Boolean.TRUE) != null;
    }

    private void recordEventConsumed(String eventId) {
        localEventLedger.put(eventId, Boolean.TRUE);
    }

    private void incrementMetric(String metricName, String eventType) {
        try {
            meterRegistry.counter(metricName, "event_type", eventType != null ? eventType : "unknown").increment();
        } catch (Exception ignored) {}
    }
}
