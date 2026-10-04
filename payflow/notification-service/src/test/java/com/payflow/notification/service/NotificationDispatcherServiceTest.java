package com.payflow.notification.service;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.events.PaymentEventTopics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    private NotificationDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        dispatcherService = new NotificationDispatcherService(redisTemplate, meterRegistry);
    }

    @Test
    @DisplayName("Should process new event and record into idempotent ledger")
    void shouldProcessNewEvent() {
        String eventId = "evt_kafka_001";
        when(valueOperations.setIfAbsent(eq("seen:kafka:event:" + eventId), eq("CONSUMED"), any(Duration.class)))
                .thenReturn(true);

        PaymentEventPayload payload = PaymentEventPayload.builder()
                .paymentId("pay_123")
                .status(PaymentStatus.CAPTURED)
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .build();

        PaymentEvent<PaymentEventPayload> event = PaymentEvent.of(
                PaymentEventTopics.PAYMENT_SUCCESS,
                "pay_123",
                "mer_acme_001",
                "corr_1",
                payload
        );
        event.setEventId(eventId);

        dispatcherService.processPaymentEvent(event);
    }

    @Test
    @DisplayName("Should suppress duplicate Kafka event via idempotent ledger")
    void shouldSuppressDuplicateEvent() {
        String eventId = "evt_kafka_dup";
        when(valueOperations.setIfAbsent(eq("seen:kafka:event:" + eventId), eq("CONSUMED"), any(Duration.class)))
                .thenReturn(false);

        PaymentEvent<PaymentEventPayload> event = PaymentEvent.of(
                PaymentEventTopics.PAYMENT_SUCCESS,
                "pay_123",
                "mer_acme_001",
                "corr_1",
                PaymentEventPayload.builder().paymentId("pay_123").build()
        );
        event.setEventId(eventId);

        dispatcherService.processPaymentEvent(event);
    }
}
