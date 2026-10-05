package com.ledgerx.reconciliation.service;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.dto.request.ProviderSettlementItemDto;
import com.ledgerx.common.dto.request.SyntheticBatchRequest;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyntheticSettlementGeneratorService {

    private final TransactionRepository transactionRepository;
    private final Random random = new Random(42); // Deterministic seed for reproducible testing

    /**
     * Generates a synthetic provider settlement batch from existing database transactions
     * and injects controlled anomalies according to requested error rates.
     */
    public List<ProviderSettlementItemDto> generateSettlementReport(SyntheticBatchRequest request) {
        log.info("Generating synthetic settlement batch for provider={} count={} duplicateRate={} mismatchRate={}",
                request.getProvider(), request.getRecordCount(), request.getDuplicateRate(), request.getAmountMismatchRate());

        List<TransactionEntity> internalTxList = transactionRepository.findAll();
        List<ProviderSettlementItemDto> settlementItems = new ArrayList<>();

        int count = request.getRecordCount();
        int internalSize = internalTxList.size();

        for (int i = 0; i < count; i++) {
            String internalId = null;
            String providerTxId = "prov_tx_" + UUID.randomUUID().toString().substring(0, 8);
            BigDecimal amount = LedgerMathUtil.scale(new BigDecimal(10 + random.nextInt(990) + "." + (10 + random.nextInt(89))));
            Currency currency = request.getCurrency() != null ? request.getCurrency() : Currency.USD;
            String status = "SUCCESS";

            if (i < internalSize) {
                TransactionEntity internal = internalTxList.get(i);
                internalId = internal.getId();
                providerTxId = internal.getProviderTransactionId() != null
                        ? internal.getProviderTransactionId()
                        : "prov_" + internal.getId().substring(0, 8);
                amount = internal.getAmount();
                currency = internal.getCurrency();
                status = internal.getStatus().name();
            }

            // Anomaly Injection 1: Missing Internal Record (phantom external charge)
            if (random.nextDouble() < request.getMissingInternalRate()) {
                internalId = null;
                providerTxId = "prov_untracked_" + UUID.randomUUID().toString().substring(0, 8);
            }

            // Anomaly Injection 2: Amount Mismatch
            if (random.nextDouble() < request.getAmountMismatchRate()) {
                BigDecimal variance = new BigDecimal((random.nextInt(20) + 1) + ".5000");
                amount = random.nextBoolean() ? amount.add(variance) : amount.subtract(variance).max(BigDecimal.ONE);
                amount = LedgerMathUtil.scale(amount);
            }

            // Anomaly Injection 3: Status Mismatch
            if (random.nextDouble() < request.getStatusMismatchRate()) {
                status = "SUCCESS".equalsIgnoreCase(status) ? "FAILED" : "SUCCESS";
            }

            BigDecimal fee = LedgerMathUtil.scale(amount.multiply(new BigDecimal("0.029")).add(new BigDecimal("0.30")));
            BigDecimal net = LedgerMathUtil.scale(amount.subtract(fee));

            ProviderSettlementItemDto item = ProviderSettlementItemDto.builder()
                    .providerTransactionId(providerTxId)
                    .internalTransactionId(internalId)
                    .amount(amount)
                    .feeAmount(fee)
                    .netAmount(net)
                    .currency(currency)
                    .status(status)
                    .settlementTimestamp(Instant.now())
                    .build();

            settlementItems.add(item);

            // Anomaly Injection 4: Duplicate Record
            if (random.nextDouble() < request.getDuplicateRate()) {
                ProviderSettlementItemDto duplicate = ProviderSettlementItemDto.builder()
                        .providerTransactionId(providerTxId)
                        .internalTransactionId(internalId)
                        .amount(amount)
                        .feeAmount(fee)
                        .netAmount(net)
                        .currency(currency)
                        .status(status)
                        .settlementTimestamp(Instant.now())
                        .build();
                settlementItems.add(duplicate);
            }
        }

        Collections.shuffle(settlementItems, random);
        log.info("Generated total {} settlement items with injected anomalies", settlementItems.size());
        return settlementItems;
    }
}
