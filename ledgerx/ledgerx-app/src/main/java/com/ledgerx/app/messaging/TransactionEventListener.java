package com.ledgerx.app.messaging;

import com.ledgerx.app.metrics.MetricsService;
import com.ledgerx.common.domain.enums.AuditAction;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.events.LedgerEvent;
import com.ledgerx.common.events.LedgerTopics;
import com.ledgerx.common.events.payload.LedgerPostedPayload;
import com.ledgerx.common.events.payload.TransactionEventPayload;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.common.exception.InsufficientFundsException;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.AuditService;
import com.ledgerx.ledger.service.DeadLetterService;
import com.ledgerx.ledger.service.HeldEventService;
import com.ledgerx.ledger.service.IdempotencyService;
import com.ledgerx.ledger.service.LedgerService;
import com.ledgerx.ledger.service.TransactionStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private static final String CONSUMER_NAME = "ledgerx-transaction-processor";

    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final AccountService accountService;
    private final TransactionStorageService transactionStorageService;
    private final HeldEventService heldEventService;
    private final AuditService auditService;
    private final DeadLetterService deadLetterService;
    private final KafkaEventProducer kafkaEventProducer;
    private final MetricsService metricsService;

    @KafkaListener(
            topics = LedgerTopics.TRANSACTIONS_VALIDATED,
            groupId = "ledgerx-transaction-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processValidatedTransaction(ConsumerRecord<String, String> record) {
        String payloadJson = record.value();
        log.info("Received Kafka message from partition={} offset={}", record.partition(), record.offset());
        metricsService.incrementEventsReceived();

        try {
            LedgerEvent<?> rawEvent = JsonUtil.fromJson(payloadJson, LedgerEvent.class);
            String eventId = rawEvent.getEventId();

            // 1. Idempotency Check
            boolean lockAcquired = idempotencyService.tryAcquireProcessingLock(eventId, CONSUMER_NAME);
            if (!lockAcquired) {
                log.warn("Idempotent skip: event {} already processed by {}", eventId, CONSUMER_NAME);
                return;
            }

            // 2. Extract Typed Payload
            TransactionEventPayload payload = JsonUtil.fromJson(
                    JsonUtil.toJson(rawEvent.getPayload()),
                    TransactionEventPayload.class
            );

            // Check if already posted (e.g. synchronous ingestion path)
            var existingTx = transactionStorageService.findById(payload.getTransactionId());
            if (existingTx.isPresent() && existingTx.get().getStatus() == TransactionStatus.POSTED) {
                log.info("Transaction {} is already posted. Marking event {} completed.", payload.getTransactionId(), eventId);
                idempotencyService.markCompleted(eventId, CONSUMER_NAME);
                return;
            }

            log.info("Processing financial transaction event: txId={} type={} amount={} {}",
                    payload.getTransactionId(), payload.getTransactionType(), payload.getAmount(), payload.getCurrency());

            // 3. Post Double-Entry Ledger Transaction
            LedgerTransactionEntity ledgerTx;
            TransactionType type = payload.getTransactionType();

            if (type == TransactionType.PAYMENT) {
                ledgerTx = ledgerService.postPayment(
                        payload.getTransactionId(),
                        payload.getMerchantId(),
                        payload.getAmount(),
                        payload.getCurrency(),
                        "Payment processed: " + payload.getTransactionId()
                );
                // Drain any held out-of-order events waiting for this merchant
                heldEventService.processPendingHeldEvents(payload.getMerchantId());
            } else if (type == TransactionType.REFUND || type == TransactionType.CHARGEBACK) {
                var merchantAcc = accountService.getOrCreateAccount(payload.getMerchantId(), com.ledgerx.common.domain.enums.AccountType.MERCHANT_PAYABLE, payload.getCurrency());
                if (merchantAcc.getBalance().compareTo(payload.getAmount()) < 0) {
                    log.warn("Holding out-of-order event {} for tx {}: merchant {} has insufficient funds ({} {} < {} {}). Deferring until payment.",
                            eventId, payload.getTransactionId(), payload.getMerchantId(), merchantAcc.getBalance(), payload.getCurrency(), payload.getAmount(), payload.getCurrency());
                    heldEventService.holdEvent(
                            eventId,
                            payload.getTransactionId(),
                            payload.getMerchantId(),
                            type,
                            payload.getAmount(),
                            payload.getCurrency(),
                            payloadJson
                    );
                    transactionStorageService.updateStatus(payload.getTransactionId(), TransactionStatus.HELD);
                    idempotencyService.markCompleted(eventId, CONSUMER_NAME);
                    return;
                }

                try {
                    if (type == TransactionType.REFUND) {
                        ledgerTx = ledgerService.postRefund(
                                payload.getTransactionId(),
                                payload.getMerchantId(),
                                payload.getAmount(),
                                payload.getCurrency(),
                                "Refund processed: " + payload.getTransactionId()
                        );
                    } else {
                        ledgerTx = ledgerService.postChargeback(
                                payload.getTransactionId(),
                                payload.getMerchantId(),
                                payload.getAmount(),
                                payload.getCurrency(),
                                "Chargeback processed: " + payload.getTransactionId()
                        );
                    }
                } catch (InsufficientFundsException ife) {
                    log.warn("Holding out-of-order event {} for tx {}: merchant {} has insufficient funds ({} {}). Deferring until payment.",
                            eventId, payload.getTransactionId(), payload.getMerchantId(), payload.getAmount(), payload.getCurrency());
                    heldEventService.holdEvent(
                            eventId,
                            payload.getTransactionId(),
                            payload.getMerchantId(),
                            type,
                            payload.getAmount(),
                            payload.getCurrency(),
                            payloadJson
                    );
                    transactionStorageService.updateStatus(payload.getTransactionId(), TransactionStatus.HELD);
                    idempotencyService.markCompleted(eventId, CONSUMER_NAME);
                    return;
                }
            } else {
                throw new IllegalArgumentException("Unsupported transaction type: " + type);
            }

            // 4. Update Internal Transaction Record Status
            transactionStorageService.updateStatus(payload.getTransactionId(), TransactionStatus.POSTED);
            metricsService.incrementLedgerTransactions();

            // 5. Emit Immutable Audit Event
            auditService.record(
                    "KAFKA_CONSUMER",
                    AuditAction.LEDGER_POSTED,
                    ledgerTx.getId(),
                    "LEDGER_TRANSACTION",
                    ledgerTx,
                    rawEvent.getEventId()
            );

            // 6. Publish Ledger Posted Event
            LedgerPostedPayload postedPayload = LedgerPostedPayload.builder()
                    .ledgerTransactionId(ledgerTx.getId())
                    .referenceId(payload.getTransactionId())
                    .transactionType(payload.getTransactionType())
                    .amount(payload.getAmount())
                    .currency(payload.getCurrency())
                    .postedAt(Instant.now())
                    .build();

            LedgerEvent<LedgerPostedPayload> postedEvent = LedgerEvent.<LedgerPostedPayload>builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("LEDGER_POSTED")
                    .aggregateId(payload.getTransactionId())
                    .producer("ledgerx-ledger-service")
                    .payload(postedPayload)
                    .build();

            kafkaEventProducer.publishEvent(LedgerTopics.LEDGER_POSTED, postedEvent);

            // 7. Complete Idempotency Lock
            idempotencyService.markCompleted(eventId, CONSUMER_NAME);
            metricsService.incrementEventsProcessed();
            log.info("Successfully processed event {} and posted ledger transaction {}", eventId, ledgerTx.getId());

        } catch (Exception e) {
            log.error("Failed to process transaction event from Kafka: {}", e.getMessage(), e);
            metricsService.incrementEventsFailed();

            // Send to DLQ
            deadLetterService.recordFailure(
                    record.key() != null ? record.key() : UUID.randomUUID().toString(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    e.getMessage(),
                    getStackTraceString(e),
                    payloadJson
            );
            metricsService.incrementDlqEvents();
        }
    }

    private String getStackTraceString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement elem : t.getStackTrace()) {
            sb.append(elem.toString()).append("\n");
            if (sb.length() > 2000) break;
        }
        return sb.toString();
    }
}
