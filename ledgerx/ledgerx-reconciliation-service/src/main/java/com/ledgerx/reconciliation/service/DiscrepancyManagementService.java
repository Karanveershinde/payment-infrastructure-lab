package com.ledgerx.reconciliation.service;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import com.ledgerx.common.domain.enums.ReconciliationStatus;
import com.ledgerx.common.dto.request.ResolveDiscrepancyRequest;
import com.ledgerx.common.dto.response.DiscrepancyResponse;
import com.ledgerx.common.dto.response.ReconciliationBatchResponse;
import com.ledgerx.common.exception.DiscrepancyNotFoundException;
import com.ledgerx.ledger.repository.TransactionRepository;
import com.ledgerx.reconciliation.domain.entity.ReconciliationBatchEntity;
import com.ledgerx.reconciliation.domain.entity.ReconciliationDiscrepancyEntity;
import com.ledgerx.reconciliation.repository.ReconciliationBatchRepository;
import com.ledgerx.reconciliation.repository.ReconciliationDiscrepancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscrepancyManagementService {

    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<ReconciliationBatchResponse> getAllBatches(Pageable pageable) {
        return batchRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toBatchResponse);
    }

    @Transactional(readOnly = true)
    public ReconciliationBatchResponse getBatchById(String batchId) {
        ReconciliationBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation batch not found: " + batchId));
        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public Page<DiscrepancyResponse> getDiscrepanciesByBatch(String batchId, Pageable pageable) {
        return discrepancyRepository.findByBatchId(batchId, pageable)
                .map(this::toDiscrepancyResponse);
    }

    @Transactional(readOnly = true)
    public DiscrepancyResponse getDiscrepancyById(String discrepancyId) {
        ReconciliationDiscrepancyEntity entity = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new DiscrepancyNotFoundException("Discrepancy not found: " + discrepancyId));
        return toDiscrepancyResponse(entity);
    }

    @Transactional
    public DiscrepancyResponse resolveDiscrepancy(String discrepancyId, ResolveDiscrepancyRequest request) {
        log.info("Resolving discrepancy id={} status={} by={}", discrepancyId, request.getResolutionStatus(), request.getResolvedBy());

        ReconciliationDiscrepancyEntity entity = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new DiscrepancyNotFoundException("Discrepancy not found: " + discrepancyId));

        entity.setResolutionStatus(request.getResolutionStatus());
        entity.setResolutionNotes(request.getResolutionNotes());
        entity.setResolvedBy(request.getResolvedBy());
        entity.setResolvedAt(Instant.now());

        ReconciliationDiscrepancyEntity saved = discrepancyRepository.save(entity);

        // If internal transaction exists, update its reconciliation status
        if (entity.getInternalTransactionId() != null && request.getResolutionStatus() == DiscrepancyResolutionStatus.RESOLVED) {
            transactionRepository.findById(entity.getInternalTransactionId()).ifPresent(tx -> {
                tx.setReconciliationStatus(ReconciliationStatus.MATCHED);
                transactionRepository.save(tx);
            });
        }

        return toDiscrepancyResponse(saved);
    }

    public DiscrepancyResponse toDiscrepancyResponse(ReconciliationDiscrepancyEntity entity) {
        return DiscrepancyResponse.builder()
                .id(entity.getId())
                .batchId(entity.getBatch().getId())
                .transactionId(entity.getInternalTransactionId())
                .providerTransactionId(entity.getProviderTransactionId())
                .provider(entity.getProvider())
                .discrepancyType(entity.getDiscrepancyType())
                .internalAmount(entity.getInternalAmount())
                .providerAmount(entity.getProviderAmount())
                .internalStatus(entity.getInternalStatus())
                .providerStatus(entity.getProviderStatus())
                .resolutionStatus(entity.getResolutionStatus())
                .resolutionNotes(entity.getResolutionNotes())
                .resolvedBy(entity.getResolvedBy())
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ReconciliationBatchResponse toBatchResponse(ReconciliationBatchEntity entity) {
        Instant createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now();
        Instant completedAt = createdAt.plusMillis(entity.getExecutionTimeMs());

        return ReconciliationBatchResponse.builder()
                .id(entity.getId())
                .provider(entity.getProvider())
                .batchDate(entity.getBatchDate())
                .totalRecords(entity.getTotalRecords())
                .matchedRecords(entity.getMatchedCount())
                .discrepantRecords(entity.getDiscrepancyCount())
                .totalInternalAmount(entity.getTotalInternalAmount())
                .totalProviderAmount(entity.getTotalProviderAmount())
                .status(entity.getStatus())
                .executionDurationMs(entity.getExecutionTimeMs())
                .createdAt(createdAt)
                .completedAt(completedAt)
                .build();
    }
}
