package com.ledgerx.app.service;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.metrics.MetricsService;
import com.ledgerx.common.domain.enums.AuditAction;
import com.ledgerx.common.domain.enums.RiskDecision;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.RiskEvaluationResponse;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.common.events.LedgerEvent;
import com.ledgerx.common.events.LedgerTopics;
import com.ledgerx.common.events.payload.TransactionEventPayload;
import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.exception.InsufficientFundsException;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.AuditService;
import com.ledgerx.ledger.service.HeldEventService;
import com.ledgerx.ledger.service.LedgerService;
import com.ledgerx.ledger.service.TransactionStorageService;
import com.ledgerx.risk.service.RiskEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionOrchestrationService {

    private final RiskEngineService riskEngineService;
    private final LedgerService ledgerService;
    private final AccountService accountService;
    private final TransactionStorageService transactionStorageService;
    private final HeldEventService heldEventService;
    private final AuditService auditService;
    private final KafkaEventProducer kafkaEventProducer;
    private final MetricsService metricsService;

    /**
     * Orchestrates end-to-end ingestion:
     * 1. Risk rule evaluation
     * 2. Persisting transaction record
     * 3. Double-entry ledger posting (if accepted)
     * 4. Audit trail persistence
     * 5. Kafka event publishing
     */
    @Transactional
    public TransactionResponse ingestTransaction(IngestTransactionRequest request, String requestId) {
        String txId = request.getTransactionId() != null ? request.getTransactionId() : UUID.randomUUID().toString();
        request.setTransactionId(txId);
        String eventId = request.getEventId() != null ? request.getEventId() : "evt_" + UUID.randomUUID().toString().substring(0, 10);
        request.setEventId(eventId);

        // Idempotency check: Return existing record if already ingested
        var existingTx = transactionStorageService.findById(txId);
        if (existingTx.isPresent()) {
            log.info("Transaction {} already ingested. Returning existing record idempotently.", txId);
            return transactionStorageService.toResponse(existingTx.get());
        }
        var existingEvent = transactionStorageService.findByEventId(eventId);
        if (existingEvent.isPresent()) {
            log.info("Event {} already ingested. Returning existing record idempotently.", eventId);
            return transactionStorageService.toResponse(existingEvent.get());
        }

        log.info("Ingesting transaction txId={} eventId={} merchant={} customer={} amount={} {}",
                txId, eventId, request.getMerchantId(), request.getCustomerId(), request.getAmount(), request.getCurrency());
        metricsService.incrementEventsReceived();

        // 1. Evaluate Risk Rules
        RiskEvaluationResponse riskResult = riskEngineService.evaluate(request);
        metricsService.recordRiskDecision(riskResult.getDecision());

        // 2. Audit Risk Evaluation
        auditService.record(
                "RISK_ENGINE",
                AuditAction.RISK_EVALUATED,
                txId,
                "RISK_DECISION",
                riskResult,
                requestId
        );

        // 3. Handle Blocked / Rejected Transactions
        if (riskResult.getDecision() == RiskDecision.REJECT) {
            log.warn("Transaction {} BLOCKED by risk engine: score={} rules={}",
                    txId, riskResult.getScore(), riskResult.getTriggeredRules());

            TransactionEntity savedBlocked = transactionStorageService.storeIncomingTransaction(
                    request,
                    TransactionStatus.BLOCKED,
                    riskResult.getScore(),
                    riskResult.getRiskLevel()
            );

            auditService.record(
                    "TRANSACTION_INGESTION",
                    AuditAction.TRANSACTION_INGESTED,
                    txId,
                    "TRANSACTION",
                    "Transaction blocked due to high risk score: " + riskResult.getScore(),
                    requestId
            );

            return transactionStorageService.toResponse(savedBlocked);
        }

        // 4. Handle Accepted / Reviewed Transactions: Post Double-Entry Ledger
        TransactionStatus initialStatus = riskResult.getDecision() == RiskDecision.REVIEW
                ? TransactionStatus.FLAGGED_RISK
                : TransactionStatus.POSTED;

        LedgerTransactionEntity ledgerTx;
        TransactionType txType = request.getTransactionType();

        if (txType == TransactionType.PAYMENT) {
            ledgerTx = ledgerService.postPayment(
                    txId,
                    request.getMerchantId(),
                    request.getAmount(),
                    request.getCurrency(),
                    "Payment for tx: " + txId
            );
            // Prerequisite payment posted: drain any held out-of-order refund events for merchant
            heldEventService.processPendingHeldEvents(request.getMerchantId());
        } else if (txType == TransactionType.REFUND || txType == TransactionType.CHARGEBACK) {
            AccountEntity merchantAccount = accountService.getOrCreateAccount(request.getMerchantId(), AccountType.MERCHANT_PAYABLE, request.getCurrency());
            if (merchantAccount.getBalance().compareTo(request.getAmount()) < 0) {
                log.warn("Holding out-of-order transaction {}: merchant {} has insufficient funds ({} {} < {} {}). Deferring until payment.",
                        txId, request.getMerchantId(), merchantAccount.getBalance(), request.getCurrency(), request.getAmount(), request.getCurrency());
                heldEventService.holdEvent(
                        eventId,
                        txId,
                        request.getMerchantId(),
                        txType,
                        request.getAmount(),
                        request.getCurrency(),
                        JsonUtil.toJson(request)
                );
                TransactionEntity savedHeld = transactionStorageService.storeIncomingTransaction(
                        request,
                        TransactionStatus.HELD,
                        riskResult.getScore(),
                        riskResult.getRiskLevel()
                );
                return transactionStorageService.toResponse(savedHeld);
            }

            if (txType == TransactionType.REFUND) {
                ledgerTx = ledgerService.postRefund(
                        txId,
                        request.getMerchantId(),
                        request.getAmount(),
                        request.getCurrency(),
                        "Refund for tx: " + txId
                );
            } else {
                ledgerTx = ledgerService.postChargeback(
                        txId,
                        request.getMerchantId(),
                        request.getAmount(),
                        request.getCurrency(),
                        "Chargeback for tx: " + txId
                );
            }
        } else {
            throw new IllegalArgumentException("Unsupported transaction type for ingestion: " + txType);
        }

        metricsService.incrementLedgerTransactions();

        // 5. Store Internal Transaction Record
        TransactionEntity savedTx = transactionStorageService.storeIncomingTransaction(
                request,
                initialStatus,
                riskResult.getScore(),
                riskResult.getRiskLevel()
        );

        // 6. Record Audit Trail
        auditService.record(
                "TRANSACTION_INGESTION",
                AuditAction.TRANSACTION_INGESTED,
                txId,
                "TRANSACTION",
                savedTx,
                requestId
        );

        auditService.record(
                "LEDGER_ENGINE",
                AuditAction.LEDGER_POSTED,
                ledgerTx.getId(),
                "LEDGER_TRANSACTION",
                ledgerTx,
                requestId
        );

        // 7. Publish Event to Kafka
        TransactionEventPayload eventPayload = TransactionEventPayload.builder()
                .transactionId(txId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .provider(request.getProvider())
                .providerTransactionId(request.getProviderTransactionId())
                .transactionType(request.getTransactionType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(initialStatus)
                .paymentMethodToken(request.getPaymentMethodToken())
                .clientTimestamp(Instant.now())
                .build();

        LedgerEvent<TransactionEventPayload> kafkaEvent = LedgerEvent.<TransactionEventPayload>builder()
                .eventId(eventId)
                .eventType("TRANSACTION_VALIDATED")
                .aggregateId(txId)
                .producer("ledgerx-ingestion-service")
                .payload(eventPayload)
                .build();

        kafkaEventProducer.publishEvent(LedgerTopics.TRANSACTIONS_VALIDATED, txId, kafkaEvent);

        metricsService.incrementEventsProcessed();
        return transactionStorageService.toResponse(savedTx);
    }
}
