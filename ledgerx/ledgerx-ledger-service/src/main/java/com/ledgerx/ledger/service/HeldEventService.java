package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.AuditAction;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.exception.InsufficientFundsException;
import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import com.ledgerx.ledger.domain.entity.HeldEventEntity;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.repository.HeldEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeldEventService {

    private final HeldEventRepository heldEventRepository;
    @Lazy private final LedgerService ledgerService;
    @Lazy private final AccountService accountService;
    @Lazy private final TransactionStorageService transactionStorageService;
    @Lazy private final AuditService auditService;

    @Transactional
    public HeldEventEntity holdEvent(
            String eventId,
            String transactionId,
            String merchantId,
            TransactionType transactionType,
            BigDecimal amount,
            Currency currency,
            String payloadJson
    ) {
        Optional<HeldEventEntity> existing = heldEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("Event {} is already held or processed with status {}", eventId, existing.get().getStatus());
            return existing.get();
        }

        HeldEventEntity entity = HeldEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .eventId(eventId)
                .transactionId(transactionId)
                .merchantId(merchantId)
                .transactionType(transactionType)
                .amount(amount)
                .currency(currency)
                .status("HELD")
                .payload(payloadJson)
                .build();

        log.info("Holding out-of-order event eventId={} txId={} merchant={} type={} amount={} {}",
                eventId, transactionId, merchantId, transactionType, amount, currency);

        return heldEventRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean isEventHeld(String eventId) {
        return heldEventRepository.findByEventId(eventId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<HeldEventEntity> getPendingHeldEventsForMerchant(String merchantId) {
        return heldEventRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, "HELD");
    }

    @Transactional
    public int processPendingHeldEvents(String merchantId) {
        List<HeldEventEntity> pending = heldEventRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, "HELD");
        if (pending.isEmpty()) {
            return 0;
        }

        log.info("Attempting to process {} held events for merchant {}", pending.size(), merchantId);
        int processedCount = 0;

        for (HeldEventEntity held : pending) {
            try {
                LedgerTransactionEntity ledgerTx;
                if (held.getTransactionType() == TransactionType.REFUND) {
                    ledgerTx = ledgerService.postRefund(
                            held.getTransactionId(),
                            held.getMerchantId(),
                            held.getAmount(),
                            held.getCurrency(),
                            "Held refund processed: " + held.getTransactionId()
                    );
                } else if (held.getTransactionType() == TransactionType.CHARGEBACK) {
                    ledgerTx = ledgerService.postChargeback(
                            held.getTransactionId(),
                            held.getMerchantId(),
                            held.getAmount(),
                            held.getCurrency(),
                            "Held chargeback processed: " + held.getTransactionId()
                    );
                } else {
                    log.warn("Unsupported held transaction type: {}", held.getTransactionType());
                    continue;
                }

                held.setStatus("PROCESSED");
                held.setProcessedAt(Instant.now());
                heldEventRepository.save(held);

                transactionStorageService.updateStatus(held.getTransactionId(), TransactionStatus.POSTED);

                auditService.record(
                        "HELD_EVENT_PROCESSOR",
                        AuditAction.LEDGER_POSTED,
                        ledgerTx.getId(),
                        "LEDGER_TRANSACTION",
                        ledgerTx,
                        held.getEventId()
                );

                processedCount++;
                log.info("Successfully processed held event eventId={} txId={} for merchant {}",
                        held.getEventId(), held.getTransactionId(), merchantId);

            } catch (InsufficientFundsException ife) {
                log.info("Held event eventId={} for merchant {} still has insufficient funds: {}. Halting drain.",
                        held.getEventId(), merchantId, ife.getMessage());
                break; // cannot process further events until more funds arrive
            } catch (Exception e) {
                log.error("Failed to process held event eventId={}: {}", held.getEventId(), e.getMessage(), e);
            }
        }

        return processedCount;
    }
}
