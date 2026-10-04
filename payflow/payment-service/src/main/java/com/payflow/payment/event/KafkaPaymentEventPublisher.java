package com.payflow.payment.event;

import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.events.PaymentEventTopics;
import com.payflow.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishEvent(String topic, PaymentEvent<PaymentEventPayload> event) {
        String key = event.getAggregateId();
        String payloadJson = JsonUtil.toJson(event);

        try {
            log.info("Publishing Kafka event [{}] to topic [{}] key [{}]", event.getEventType(), topic, key);
            kafkaTemplate.send(topic, key, payloadJson)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish Kafka event [{}] to topic [{}]: {}", event.getEventId(), topic, ex.getMessage());
                        } else {
                            log.debug("Kafka event [{}] published successfully at offset {}", event.getEventId(), result.getRecordMetadata().offset());
                        }
                    });

            // Also forward to central notification topic
            if (!PaymentEventTopics.NOTIFICATIONS_TOPIC.equals(topic)) {
                kafkaTemplate.send(PaymentEventTopics.NOTIFICATIONS_TOPIC, key, payloadJson);
            }

        } catch (Exception e) {
            log.warn("Kafka broker unreachable during event publication: {}. Resuming transaction flow.", e.getMessage());
        }
    }
}
