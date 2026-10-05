package com.ledgerx.reconciliation.service;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.ReconciliationStatus;
import com.ledgerx.common.dto.request.ProviderSettlementItemDto;
import com.ledgerx.common.dto.request.RunReconciliationRequest;
import com.ledgerx.common.dto.response.ReconciliationBatchResponse;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.repository.TransactionRepository;
import com.ledgerx.reconciliation.domain.entity.ProviderSettlementRecordEntity;
import com.ledgerx.reconciliation.domain.entity.ReconciliationBatchEntity;
import com.ledgerx.reconciliation.domain.entity.ReconciliationDiscrepancyEntity;
import com.ledgerx.reconciliation.repository.ProviderSettlementRecordRepository;
import com.ledgerx.reconciliation.repository.ReconciliationBatchRepository;
import com.ledgerx.reconciliation.repository.ReconciliationDiscrepancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationEngineService {

    private final ReconciliationBatchRepository batchRepository;
    private final ProviderSettlementRecordRepository settlementRecordRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public ReconciliationBatchResponse runReconciliation(RunReconciliationRequest request) {
        Instant startTime = Instant.now();
        String batchId = UUID.randomUUID().toString();
        LocalDate batchDate = request.getBatchDate() != null ? request.getBatchDate() : LocalDate.now();
        ProviderType provider = request.getProvider();

        log.info("Starting reconciliation batch id={} provider={} date={} records={}",
                batchId, provider, batchDate, request.getSettlementRecords().size());

        ReconciliationBatchEntity batch = ReconciliationBatchEntity.builder()
                .id(batchId)
                .batchDate(batchDate)
                .provider(provider)
                .totalRecords(request.getSettlementRecords().size())
                .matchedCount(0)
                .discrepancyCount(0)
                .totalInternalAmount(LedgerMathUtil.ZERO)
                .totalProviderAmount(LedgerMathUtil.ZERO)
                .status("PROCESSING")
                .executionTimeMs(0)
                .build();

        batch = batchRepository.save(batch);

        List<ProviderSettlementItemDto> items = request.getSettlementRecords();
        List<ProviderSettlementRecordEntity> settlementEntities = new ArrayList<>();
        List<ReconciliationDiscrepancyEntity> discrepancies = new ArrayList<>();

        Set<String> seenProviderTxIds = new HashSet<>();
        Set<String> matchedInternalIds = new HashSet<>();

        BigDecimal totalProviderAmount = LedgerMathUtil.ZERO;
        BigDecimal totalInternalAmount = LedgerMathUtil.ZERO;
        int matchedCount = 0;

        for (ProviderSettlementItemDto item : items) {
            BigDecimal provAmount = LedgerMathUtil.scale(item.getAmount());
            totalProviderAmount = LedgerMathUtil.add(totalProviderAmount, provAmount);

            ProviderSettlementRecordEntity recordEntity = ProviderSettlementRecordEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .batch(batch)
                    .provider(provider)
                    .providerTransactionId(item.getProviderTransactionId())
                    .internalTransactionId(item.getInternalTransactionId())
                    .amount(provAmount)
                    .currency(item.getCurrency())
                    .status(mapProviderStatus(item.getStatus()))
                    .settlementDate(item.getSettlementTimestamp() != null ? item.getSettlementTimestamp() : Instant.now())
                    .rawRecord(JsonUtil.toJson(item))
                    .build();
            settlementEntities.add(recordEntity);

            // 1. Check for Duplicate Provider Record
            if (seenProviderTxIds.contains(item.getProviderTransactionId())) {
                log.warn("Duplicate provider settlement record detected: {}", item.getProviderTransactionId());
                discrepancies.add(createDiscrepancy(
                        batch,
                        provider,
                        item.getInternalTransactionId(),
                        item.getProviderTransactionId(),
                        DiscrepancyType.DUPLICATE_PROVIDER,
                        null,
                        provAmount,
                        null,
                        item.getStatus(),
                        "Duplicate settlement record in provider batch report"
                ));
                continue;
            }
            seenProviderTxIds.add(item.getProviderTransactionId());

            // 2. Lookup Internal Transaction
            Optional<TransactionEntity> internalOpt = Optional.empty();
            if (item.getInternalTransactionId() != null) {
                internalOpt = transactionRepository.findById(item.getInternalTransactionId());
            }
            if (internalOpt.isEmpty() && item.getProviderTransactionId() != null) {
                internalOpt = transactionRepository.findByProviderTransactionId(item.getProviderTransactionId());
            }

            if (internalOpt.isEmpty()) {
                // Discrepancy: MISSING_INTERNAL
                log.warn("Discrepancy detected: MISSING_INTERNAL for providerTxId={}", item.getProviderTransactionId());
                discrepancies.add(createDiscrepancy(
                        batch,
                        provider,
                        null,
                        item.getProviderTransactionId(),
                        DiscrepancyType.MISSING_INTERNAL,
                        null,
                        provAmount,
                        null,
                        item.getStatus(),
                        "Transaction settled at provider but missing from internal ledger records"
                ));
                continue;
            }

            TransactionEntity internal = internalOpt.get();
            matchedInternalIds.add(internal.getId());
            BigDecimal intAmount = LedgerMathUtil.scale(internal.getAmount());
            totalInternalAmount = LedgerMathUtil.add(totalInternalAmount, intAmount);

            // 3. Check Currency Mismatch
            if (internal.getCurrency() != item.getCurrency()) {
                log.warn("Discrepancy detected: CURRENCY_MISMATCH for internalId={}", internal.getId());
                discrepancies.add(createDiscrepancy(
                        batch,
                        provider,
                        internal.getId(),
                        item.getProviderTransactionId(),
                        DiscrepancyType.CURRENCY_MISMATCH,
                        intAmount,
                        provAmount,
                        internal.getStatus().name(),
                        item.getStatus(),
                        String.format("Currency mismatch: internal=%s, provider=%s", internal.getCurrency(), item.getCurrency())
                ));
                internal.setReconciliationStatus(ReconciliationStatus.CURRENCY_MISMATCH);
                transactionRepository.save(internal);
                continue;
            }

            // 4. Check Amount Mismatch
            if (intAmount.compareTo(provAmount) != 0) {
                log.warn("Discrepancy detected: AMOUNT_MISMATCH for internalId={} (internal={}, provider={})",
                        internal.getId(), intAmount, provAmount);
                discrepancies.add(createDiscrepancy(
                        batch,
                        provider,
                        internal.getId(),
                        item.getProviderTransactionId(),
                        DiscrepancyType.AMOUNT_MISMATCH,
                        intAmount,
                        provAmount,
                        internal.getStatus().name(),
                        item.getStatus(),
                        String.format("Amount mismatch: internal=%s, provider=%s, diff=%s",
                                intAmount, provAmount, intAmount.subtract(provAmount).abs())
                ));
                internal.setReconciliationStatus(ReconciliationStatus.AMOUNT_MISMATCH);
                transactionRepository.save(internal);
                continue;
            }

            // 5. Check Status Mismatch
            boolean isInternalSuccess = internal.getStatus() == com.ledgerx.common.domain.enums.TransactionStatus.POSTED 
                    || internal.getStatus() == com.ledgerx.common.domain.enums.TransactionStatus.RECONCILED;
            boolean isProviderSuccess = "SUCCESS".equalsIgnoreCase(item.getStatus()) 
                    || "POSTED".equalsIgnoreCase(item.getStatus()) 
                    || "SETTLED".equalsIgnoreCase(item.getStatus());

            if (isInternalSuccess != isProviderSuccess) {
                log.warn("Discrepancy detected: STATUS_MISMATCH for internalId={} (internal={}, provider={})",
                        internal.getId(), internal.getStatus(), item.getStatus());
                discrepancies.add(createDiscrepancy(
                        batch,
                        provider,
                        internal.getId(),
                        item.getProviderTransactionId(),
                        DiscrepancyType.STATUS_MISMATCH,
                        intAmount,
                        provAmount,
                        internal.getStatus().name(),
                        item.getStatus(),
                        String.format("Status mismatch: internal=%s, provider=%s", internal.getStatus(), item.getStatus())
                ));
                internal.setReconciliationStatus(ReconciliationStatus.STATUS_MISMATCH);
                transactionRepository.save(internal);
                continue;
            }

            // 6. Valid Match!
            matchedCount++;
            internal.setReconciliationStatus(ReconciliationStatus.MATCHED);
            transactionRepository.save(internal);
        }

        // Persist settlement records & discrepancies
        settlementRecordRepository.saveAll(settlementEntities);
        if (!discrepancies.isEmpty()) {
            discrepancyRepository.saveAll(discrepancies);
        }

        long executionDurationMs = Duration.between(startTime, Instant.now()).toMillis();

        batch.setTotalRecords(items.size());
        batch.setMatchedCount(matchedCount);
        batch.setDiscrepancyCount(discrepancies.size());
        batch.setTotalInternalAmount(totalInternalAmount);
        batch.setTotalProviderAmount(totalProviderAmount);
        batch.setStatus("COMPLETED");
        batch.setExecutionTimeMs(executionDurationMs);

        ReconciliationBatchEntity savedBatch = batchRepository.save(batch);

        log.info("Reconciliation batch {} complete: matched={} discrepancies={} duration={}ms",
                batchId, matchedCount, discrepancies.size(), executionDurationMs);

        return toBatchResponse(savedBatch);
    }

    private com.ledgerx.common.domain.enums.TransactionStatus mapProviderStatus(String status) {
        if (status == null) return com.ledgerx.common.domain.enums.TransactionStatus.INGESTED;
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "SUCCESS", "SETTLED", "POSTED" -> com.ledgerx.common.domain.enums.TransactionStatus.POSTED;
            case "FAILED", "DECLINED", "ERROR" -> com.ledgerx.common.domain.enums.TransactionStatus.FAILED;
            case "BLOCKED", "FLAGGED" -> com.ledgerx.common.domain.enums.TransactionStatus.BLOCKED;
            default -> com.ledgerx.common.domain.enums.TransactionStatus.INGESTED;
        };
    }

    private ReconciliationDiscrepancyEntity createDiscrepancy(
            ReconciliationBatchEntity batch,
            ProviderType provider,
            String internalId,
            String providerTxId,
            DiscrepancyType type,
            BigDecimal internalAmount,
            BigDecimal providerAmount,
            String internalStatus,
            String providerStatus,
            String notes
    ) {
        return ReconciliationDiscrepancyEntity.builder()
                .id(UUID.randomUUID().toString())
                .batch(batch)
                .provider(provider)
                .internalTransactionId(internalId)
                .providerTransactionId(providerTxId)
                .discrepancyType(type)
                .internalAmount(internalAmount)
                .providerAmount(providerAmount)
                .internalStatus(internalStatus)
                .providerStatus(providerStatus)
                .resolutionStatus(DiscrepancyResolutionStatus.OPEN)
                .resolutionNotes(notes)
                .build();
    }

    public ReconciliationBatchResponse toBatchResponse(ReconciliationBatchEntity entity) {
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
