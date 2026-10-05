package com.payflow.payment.event;

import com.payflow.payment.domain.entity.OutboxEventEntity;
import com.payflow.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 500)
    public void processOutbox() {
        try {
            List<OutboxEventEntity> pendingEvents = outboxEventRepository.findPendingEvents(PageRequest.of(0, 50));
            for (OutboxEventEntity event : pendingEvents) {
                publishSingleEvent(event);
            }
        } catch (Exception e) {
            log.debug("Outbox processor cycle skipped: {}", e.getMessage());
        }
    }

    public boolean publishSingleEvent(OutboxEventEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
            event.setStatus("PUBLISHED");
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
            log.debug("Outbox event [{}] published to topic [{}]", event.getId(), event.getTopic());
            return true;
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown publication error";
            event.setErrorMessage(errMsg.substring(0, Math.min(errMsg.length(), 500)));
            if (event.getRetryCount() >= 5) {
                event.setStatus("FAILED");
            }
            outboxEventRepository.save(event);
            log.warn("Failed to publish outbox event [{}] (attempt {}): {}", event.getId(), event.getRetryCount(), e.getMessage());
            return false;
        }
    }
}
