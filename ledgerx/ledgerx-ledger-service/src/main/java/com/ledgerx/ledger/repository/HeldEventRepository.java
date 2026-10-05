package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.HeldEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HeldEventRepository extends JpaRepository<HeldEventEntity, String> {

    Optional<HeldEventEntity> findByEventId(String eventId);

    List<HeldEventEntity> findByMerchantIdAndStatusOrderByCreatedAtAsc(String merchantId, String status);

    List<HeldEventEntity> findByStatusOrderByCreatedAtAsc(String status);
}
