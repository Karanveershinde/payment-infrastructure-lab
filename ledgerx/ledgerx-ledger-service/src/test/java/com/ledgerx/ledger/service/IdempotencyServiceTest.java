package com.ledgerx.ledger.service;

import com.ledgerx.ledger.domain.entity.ProcessedEventEntity;
import com.ledgerx.ledger.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("Should successfully acquire processing lock for first-time event")
    void shouldAcquireLockFirstTime() {
        String eventId = "evt-12345";
        String consumer = "ledger-consumer";

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumer)).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean acquired = idempotencyService.tryAcquireProcessingLock(eventId, consumer);

        assertThat(acquired).isTrue();
        verify(processedEventRepository).saveAndFlush(any(ProcessedEventEntity.class));
    }

    @Test
    @DisplayName("Should reject duplicate event when already recorded")
    void shouldRejectDuplicateEvent() {
        String eventId = "evt-12345";
        String consumer = "ledger-consumer";

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumer)).thenReturn(true);

        boolean acquired = idempotencyService.tryAcquireProcessingLock(eventId, consumer);

        assertThat(acquired).isFalse();
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should handle concurrent race condition via database unique constraint violation")
    void shouldHandleConcurrentRaceCondition() {
        String eventId = "evt-race-999";
        String consumer = "ledger-consumer";

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumer)).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key violation"));

        boolean acquired = idempotencyService.tryAcquireProcessingLock(eventId, consumer);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("Should mark event as completed")
    void shouldMarkCompleted() {
        String eventId = "evt-123";
        String consumer = "ledger-consumer";

        ProcessedEventEntity entity = ProcessedEventEntity.builder()
                .eventId(eventId)
                .consumerName(consumer)
                .status("PROCESSING")
                .build();

        when(processedEventRepository.findByEventIdAndConsumerName(eventId, consumer)).thenReturn(Optional.of(entity));

        idempotencyService.markCompleted(eventId, consumer);

        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        verify(processedEventRepository).save(entity);
    }
}
