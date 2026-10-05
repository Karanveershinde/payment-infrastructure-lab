package com.ledgerx.reconciliation.repository;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.reconciliation.domain.entity.ReconciliationDiscrepancyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancyEntity, String> {

    List<ReconciliationDiscrepancyEntity> findByBatchId(String batchId);

    Page<ReconciliationDiscrepancyEntity> findByBatchId(String batchId, Pageable pageable);

    Page<ReconciliationDiscrepancyEntity> findByResolutionStatus(DiscrepancyResolutionStatus status, Pageable pageable);

    Page<ReconciliationDiscrepancyEntity> findByDiscrepancyType(DiscrepancyType discrepancyType, Pageable pageable);

    long countByResolutionStatus(DiscrepancyResolutionStatus status);
}
