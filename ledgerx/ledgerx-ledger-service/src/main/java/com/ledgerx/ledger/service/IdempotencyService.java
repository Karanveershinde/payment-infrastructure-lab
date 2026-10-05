package com.ledgerx.ledger.service;

import com.ledgerx.ledger.domain.entity.ProcessedEventEntity;
import com.ledgerx.ledger.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean isEventProcessed(String eventId, String consumerName) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    /**
     * Mark an event as processed for a specific consumer.
     * Returns true if newly marked, false if already processed (duplicate detected).
     */
    @Transactional
    public boolean tryAcquireProcessingLock(String eventId, String consumerName) {
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.warn("Duplicate event detected: eventId={} for consumer={}", eventId, consumerName);
            return false;
        }

        try {
            ProcessedEventEntity entity = ProcessedEventEntity.builder()
                    .eventId(eventId)
                    .consumerName(consumerName)
                    .status("PROCESSING")
                    .build();
            processedEventRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate event detected via DB constraint: eventId={} for consumer={}", eventId, consumerName);
            return false;
        }
    }

    @Transactional
    public void markCompleted(String eventId, String consumerName) {
        processedEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> {
                    entity.setStatus("COMPLETED");
                    processedEventRepository.save(entity);
                });
    }

    @Transactional
    public void markFailed(String eventId, String consumerName) {
        processedEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .ifPresent(entity -> {
                    entity.setStatus("FAILED");
                    processedEventRepository.save(entity);
                });
    }
}
