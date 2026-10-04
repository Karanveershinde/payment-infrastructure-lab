package com.payflow.notification.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.events.PaymentEventTopics;
import com.payflow.common.util.JsonUtil;
import com.payflow.notification.service.NotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final NotificationDispatcherService dispatcherService;

    @KafkaListener(
            topics = {
                    PaymentEventTopics.PAYMENT_CREATED,
                    PaymentEventTopics.PAYMENT_PROCESSING,
                    PaymentEventTopics.PAYMENT_SUCCESS,
                    PaymentEventTopics.PAYMENT_FAILED,
                    PaymentEventTopics.PAYMENT_CANCELLED,
                    PaymentEventTopics.PAYMENT_REFUNDED,
                    PaymentEventTopics.PAYMENT_WEBHOOK_RECEIVED,
                    PaymentEventTopics.NOTIFICATIONS_TOPIC
            },
            groupId = "${spring.kafka.consumer.group-id:payflow-notification-group}"
    )
    public void onPaymentEvent(String message) {
        try {
            log.debug("Received raw Kafka message: {}", message);
            PaymentEvent<PaymentEventPayload> event = JsonUtil.getObjectMapper().readValue(
                    message,
                    new TypeReference<PaymentEvent<PaymentEventPayload>>() {}
            );

            dispatcherService.processPaymentEvent(event);

        } catch (Exception e) {
            log.error("Failed to deserialize and process Kafka payment event: {}", e.getMessage(), e);
        }
    }
}
