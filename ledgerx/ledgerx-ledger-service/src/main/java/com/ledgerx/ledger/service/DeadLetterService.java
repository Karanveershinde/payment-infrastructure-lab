package com.ledgerx.ledger.service;

import com.ledgerx.common.dto.response.DlqEventResponse;
import com.ledgerx.ledger.domain.entity.DeadLetterEventEntity;
import com.ledgerx.ledger.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterEventRepository deadLetterEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeadLetterEventEntity recordFailure(
            String eventId,
            String topic,
            Integer partition,
            Long offset,
            String failureReason,
            String stacktrace,
            String payload
    ) {
        log.error("Recording Dead Letter Event: eventId={} topic={} reason={}", eventId, topic, failureReason);

        Optional<DeadLetterEventEntity> existing = deadLetterEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            DeadLetterEventEntity dlq = existing.get();
            dlq.setRetryCount(dlq.getRetryCount() + 1);
            dlq.setFailureReason(failureReason);
            dlq.setStacktrace(stacktrace);
            dlq.setLastFailedAt(Instant.now());
            dlq.setStatus("FAILED");
            return deadLetterEventRepository.save(dlq);
        }

        DeadLetterEventEntity dlq = DeadLetterEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .eventId(eventId)
                .topic(topic)
                .partitionNum(partition)
                .offsetNum(offset)
                .failureReason(failureReason != null && failureReason.length() > 1000 ? failureReason.substring(0, 1000) : failureReason)
                .stacktrace(stacktrace)
                .retryCount(1)
                .payload(payload)
                .status("FAILED")
                .firstFailedAt(Instant.now())
                .lastFailedAt(Instant.now())
                .build();

        return deadLetterEventRepository.save(dlq);
    }

    @Transactional(readOnly = true)
    public Page<DlqEventResponse> getDlqEvents(Pageable pageable) {
        return deadLetterEventRepository.findAllByOrderByLastFailedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DeadLetterEventEntity> getDlqEventById(String id) {
        return deadLetterEventRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<DeadLetterEventEntity> getDlqEventByEventId(String eventId) {
        return deadLetterEventRepository.findByEventId(eventId);
    }

    @Transactional
    public void markReplayed(String id) {
        deadLetterEventRepository.findById(id).ifPresent(dlq -> {
            dlq.setStatus("REPLAYED");
            dlq.setReplayedAt(Instant.now());
            deadLetterEventRepository.save(dlq);
            log.info("Marked DLQ record {} as REPLAYED", id);
        });
    }

    private DlqEventResponse toResponse(DeadLetterEventEntity entity) {
        return DlqEventResponse.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .topic(entity.getTopic())
                .partitionNum(entity.getPartitionNum())
                .offsetNum(entity.getOffsetNum())
                .failureReason(entity.getFailureReason())
                .retryCount(entity.getRetryCount())
                .payload(entity.getPayload())
                .status(entity.getStatus())
                .firstFailedAt(entity.getFirstFailedAt())
                .lastFailedAt(entity.getLastFailedAt())
                .replayedAt(entity.getReplayedAt())
                .build();
    }
}
