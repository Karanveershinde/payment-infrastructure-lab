package com.ledgerx.reconciliation.repository;

import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.reconciliation.domain.entity.ReconciliationBatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatchEntity, String> {

    List<ReconciliationBatchEntity> findByProviderAndBatchDate(ProviderType provider, LocalDate batchDate);

    Page<ReconciliationBatchEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
