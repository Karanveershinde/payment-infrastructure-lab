package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.DeadLetterEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, String> {

    Optional<DeadLetterEventEntity> findByEventId(String eventId);

    List<DeadLetterEventEntity> findByStatusOrderByLastFailedAtDesc(String status);

    Page<DeadLetterEventEntity> findAllByOrderByLastFailedAtDesc(Pageable pageable);

    long countByStatus(String status);
}
