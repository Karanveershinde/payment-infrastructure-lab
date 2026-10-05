package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.ProcessedEventId> {

    Optional<ProcessedEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
