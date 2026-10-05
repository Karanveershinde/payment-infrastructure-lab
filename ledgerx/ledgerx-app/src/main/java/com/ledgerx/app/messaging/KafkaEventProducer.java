package com.ledgerx.app.messaging;

import com.ledgerx.app.metrics.MetricsService;
import com.ledgerx.common.events.LedgerEvent;
import com.ledgerx.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MetricsService metricsService;

    public void publishEvent(String topic, String key, LedgerEvent<?> event) {
        String payloadJson = JsonUtil.toJson(event);
        log.info("Publishing Kafka event to topic={} key={} type={}", topic, key, event.getEventType());

        kafkaTemplate.send(topic, key, payloadJson)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully published event {} to offset {}",
                                event.getEventId(), result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to publish event {} to topic {}: {}",
                                event.getEventId(), topic, ex.getMessage());
                        metricsService.incrementEventsFailed();
                    }
                });
    }

    public void publishEvent(String topic, LedgerEvent<?> event) {
        publishEvent(topic, event.getAggregateId(), event);
    }
}
