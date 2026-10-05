package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.ReconciliationStatus;
import com.ledgerx.common.domain.enums.RiskLevel;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStorageService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionEntity storeIncomingTransaction(
            IngestTransactionRequest request,
            TransactionStatus initialStatus,
            Integer riskScore,
            RiskLevel riskLevel
    ) {
        String id = request.getTransactionId() != null ? request.getTransactionId() : UUID.randomUUID().toString();
        String eventId = request.getEventId() != null ? request.getEventId() : UUID.randomUUID().toString();

        TransactionEntity entity = TransactionEntity.builder()
                .id(id)
                .eventId(eventId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .provider(request.getProvider())
                .providerTransactionId(request.getProviderTransactionId())
                .transactionType(request.getTransactionType())
                .amount(LedgerMathUtil.scale(request.getAmount()))
                .currency(request.getCurrency())
                .status(initialStatus)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .rawPayload(JsonUtil.toJson(request))
                .build();

        log.info("Persisting internal transaction id={} eventId={} merchantId={} amount={} {}",
                id, eventId, request.getMerchantId(), request.getAmount(), request.getCurrency());
        return transactionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionResponse(String id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionEntity> findById(String id) {
        return transactionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionEntity> findByEventId(String eventId) {
        return transactionRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionEntity> findByProviderTransactionId(String providerTransactionId) {
        return transactionRepository.findByProviderTransactionId(providerTransactionId);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByMerchant(String merchantId) {
        return transactionRepository.findByMerchantId(merchantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateStatus(String transactionId, TransactionStatus status) {
        transactionRepository.findById(transactionId).ifPresent(entity -> {
            entity.setStatus(status);
            transactionRepository.save(entity);
        });
    }

    @Transactional
    public void updateReconciliationStatus(String transactionId, ReconciliationStatus reconciliationStatus) {
        transactionRepository.findById(transactionId).ifPresent(entity -> {
            entity.setReconciliationStatus(reconciliationStatus);
            transactionRepository.save(entity);
        });
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        return TransactionResponse.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .merchantId(entity.getMerchantId())
                .customerId(entity.getCustomerId())
                .provider(entity.getProvider())
                .providerTransactionId(entity.getProviderTransactionId())
                .transactionType(entity.getTransactionType())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .riskScore(entity.getRiskScore())
                .riskLevel(entity.getRiskLevel())
                .reconciliationStatus(entity.getReconciliationStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
