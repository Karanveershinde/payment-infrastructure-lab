package com.ledgerx.ledger.repository;

import com.ledgerx.common.domain.enums.ReconciliationStatus;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    Optional<TransactionEntity> findByEventId(String eventId);

    Optional<TransactionEntity> findByProviderTransactionId(String providerTransactionId);

    List<TransactionEntity> findByMerchantId(String merchantId);

    List<TransactionEntity> findByCustomerId(String customerId);

    Page<TransactionEntity> findByReconciliationStatus(ReconciliationStatus status, Pageable pageable);

    List<TransactionEntity> findByProviderAndCreatedAtBetween(
            String provider,
            Instant startTime,
            Instant endTime
    );

    List<TransactionEntity> findByCustomerIdAndCreatedAtAfter(
            String customerId,
            Instant since
    );

    long countByCustomerIdAndStatusAndCreatedAtAfter(
            String customerId,
            TransactionStatus status,
            Instant since
    );
}
