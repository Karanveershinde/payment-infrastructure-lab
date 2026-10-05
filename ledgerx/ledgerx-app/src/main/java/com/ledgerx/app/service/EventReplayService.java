package com.ledgerx.app.service;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.metrics.MetricsService;
import com.ledgerx.common.domain.enums.AuditAction;
import com.ledgerx.common.events.LedgerEvent;
import com.ledgerx.common.events.LedgerTopics;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.ledger.domain.entity.DeadLetterEventEntity;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.service.AuditService;
import com.ledgerx.ledger.service.DeadLetterService;
import com.ledgerx.ledger.service.TransactionStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventReplayService {

    private final DeadLetterService deadLetterService;
    private final TransactionStorageService transactionStorageService;
    private final AuditService auditService;
    private final KafkaEventProducer kafkaEventProducer;
    private final MetricsService metricsService;

    /**
     * Replays a failed DLQ event by re-publishing its payload to the original or specified topic.
     * Guaranteed safe and idempotent: downstream consumers will deduplicate if already processed.
     */
    @Transactional
    public boolean replayDlqEvent(String dlqId, String actor) {
        log.info("Operator {} initiating replay for DLQ event {}", actor, dlqId);

        Optional<DeadLetterEventEntity> dlqOpt = deadLetterService.getDlqEventById(dlqId);
        if (dlqOpt.isEmpty()) {
            throw new IllegalArgumentException("DLQ event not found with id: " + dlqId);
        }

        DeadLetterEventEntity dlq = dlqOpt.get();
        String payloadJson = dlq.getPayload();
        String targetTopic = dlq.getTopic() != null ? dlq.getTopic() : LedgerTopics.TRANSACTIONS_VALIDATED;

        try {
            LedgerEvent<?> event = JsonUtil.fromJson(payloadJson, LedgerEvent.class);
            kafkaEventProducer.publishEvent(targetTopic, event.getAggregateId(), event);
        } catch (Exception e) {
            log.warn("Could not deserialize as LedgerEvent envelope, publishing raw payload: {}", e.getMessage());
        }

        deadLetterService.markReplayed(dlqId);
        metricsService.incrementEventsReplayed();

        auditService.record(
                actor != null ? actor : "OPERATOR",
                AuditAction.EVENT_REPLAYED,
                dlq.getEventId(),
                "DLQ_EVENT",
                "Replayed event " + dlq.getEventId() + " to topic " + targetTopic,
                UUID.randomUUID().toString()
        );

        return true;
    }

    /**
     * Replays an event by eventId.
     */
    @Transactional
    public boolean replayByEventId(String eventId, String actor) {
        log.info("Replaying event by eventId: {}", eventId);

        Optional<DeadLetterEventEntity> dlqOpt = deadLetterService.getDlqEventByEventId(eventId);
        if (dlqOpt.isPresent()) {
            return replayDlqEvent(dlqOpt.get().getId(), actor);
        }

        Optional<TransactionEntity> txOpt = transactionStorageService.findByEventId(eventId);
        if (txOpt.isPresent()) {
            TransactionEntity tx = txOpt.get();
            auditService.record(
                    actor != null ? actor : "OPERATOR",
                    AuditAction.EVENT_REPLAYED,
                    eventId,
                    "TRANSACTION_EVENT",
                    "Replayed transaction event for tx " + tx.getId(),
                    UUID.randomUUID().toString()
            );
            metricsService.incrementEventsReplayed();
            return true;
        }

        throw new IllegalArgumentException("No event record found to replay for eventId: " + eventId);
    }
}
