package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.EntryType;
import com.ledgerx.common.domain.enums.LedgerTransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.response.LedgerTransactionResponse;
import com.ledgerx.common.exception.InsufficientFundsException;
import com.ledgerx.common.exception.LedgerInvariantException;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import com.ledgerx.ledger.domain.entity.LedgerEntryEntity;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.repository.AccountRepository;
import com.ledgerx.ledger.repository.LedgerEntryRepository;
import com.ledgerx.ledger.repository.LedgerTransactionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Getter
    @Builder
    public static class EntryInstruction {
        private final String accountId;
        private final EntryType entryType;
        private final BigDecimal amount;
        private final Currency currency;
    }

    /**
     * Post a multi-entry double-entry ledger transaction atomically.
     * Enforces the mathematical invariant sum(debits) == sum(credits).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransactionEntity postTransaction(
            String referenceId,
            TransactionType transactionType,
            Currency currency,
            BigDecimal amount,
            String description,
            List<EntryInstruction> instructions
    ) {
        log.info("Posting double-entry transaction ref={} type={} amount={} {}",
                referenceId, transactionType, amount, currency);

        if (instructions == null || instructions.size() < 2) {
            throw new LedgerInvariantException("A double-entry transaction must contain at least 2 entries.");
        }

        // Validate double-entry equality
        List<LedgerMathUtil.DoubleEntryItem> validationItems = instructions.stream()
                .map(i -> new LedgerMathUtil.DoubleEntryItem(i.getEntryType(), i.getAmount()))
                .collect(Collectors.toList());

        LedgerMathUtil.validateDoubleEntry(validationItems);

        // Create transaction record
        String txId = UUID.randomUUID().toString();
        LedgerTransactionEntity transaction = LedgerTransactionEntity.builder()
                .id(txId)
                .referenceId(referenceId)
                .transactionType(transactionType)
                .currency(currency)
                .amount(LedgerMathUtil.scale(amount))
                .description(description)
                .status(LedgerTransactionStatus.POSTED)
                .build();

        LedgerTransactionEntity savedTx = ledgerTransactionRepository.save(transaction);

        // Process entries and mutate account balances safely
        List<LedgerEntryEntity> entryEntities = new ArrayList<>();
        for (EntryInstruction instr : instructions) {
            AccountEntity account = accountService.getAccountById(instr.getAccountId());
            accountService.validateAccountActive(account);

            if (account.getCurrency() != instr.getCurrency()) {
                throw new LedgerInvariantException(String.format(
                        "Account currency %s does not match entry currency %s",
                        account.getCurrency(), instr.getCurrency()));
            }

            BigDecimal entryAmount = LedgerMathUtil.scale(instr.getAmount());
            if (entryAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new LedgerInvariantException("Entry amount must be strictly greater than zero.");
            }

            // Atomic balance delta increment in PostgreSQL/H2 with non-negative invariant
            BigDecimal delta = calculateDelta(account.getAccountType(), instr.getEntryType(), entryAmount);
            int rowsUpdated = accountRepository.updateBalanceDelta(account.getId(), delta);
            if (rowsUpdated == 0) {
                log.warn("Insufficient funds for account {} ({}) attempting delta {}",
                        account.getAccountNumber(), account.getId(), delta);
                throw new InsufficientFundsException(String.format(
                        "Insufficient funds on account %s: cannot apply %s entry of %s %s (delta %s)",
                        account.getAccountNumber(), instr.getEntryType(), entryAmount, instr.getCurrency(), delta));
            }

            LedgerEntryEntity entry = LedgerEntryEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .ledgerTransaction(savedTx)
                    .account(account)
                    .entryType(instr.getEntryType())
                    .amount(entryAmount)
                    .currency(instr.getCurrency())
                    .build();

            entryEntities.add(entry);
        }

        ledgerEntryRepository.saveAll(entryEntities);
        log.info("Successfully posted ledger transaction id={} ref={} with {} entries",
                txId, referenceId, entryEntities.size());
        return savedTx;
    }

    /**
     * Creates a standard payment posting:
     * DEBIT: Clearing Asset Account
     * CREDIT: Merchant Payable Account
     */
    @Transactional
    public LedgerTransactionEntity postPayment(
            String referenceId,
            String merchantId,
            BigDecimal amount,
            Currency currency,
            String description
    ) {
        AccountEntity clearingAccount = accountService.getOrCreateAccount("SYSTEM_CLEARING", AccountType.CLEARING, currency);
        AccountEntity merchantAccount = accountService.getOrCreateAccount(merchantId, AccountType.MERCHANT_PAYABLE, currency);

        List<EntryInstruction> instructions = List.of(
                EntryInstruction.builder()
                        .accountId(clearingAccount.getId())
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .currency(currency)
                        .build(),
                EntryInstruction.builder()
                        .accountId(merchantAccount.getId())
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .currency(currency)
                        .build()
        );

        return postTransaction(referenceId, TransactionType.PAYMENT, currency, amount, description, instructions);
    }

    /**
     * Creates a standard refund posting:
     * DEBIT: Merchant Payable Account
     * CREDIT: Clearing Asset Account
     */
    @Transactional
    public LedgerTransactionEntity postRefund(
            String referenceId,
            String merchantId,
            BigDecimal amount,
            Currency currency,
            String description
    ) {
        AccountEntity clearingAccount = accountService.getOrCreateAccount("SYSTEM_CLEARING", AccountType.CLEARING, currency);
        AccountEntity merchantAccount = accountService.getOrCreateAccount(merchantId, AccountType.MERCHANT_PAYABLE, currency);

        List<EntryInstruction> instructions = List.of(
                EntryInstruction.builder()
                        .accountId(merchantAccount.getId())
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .currency(currency)
                        .build(),
                EntryInstruction.builder()
                        .accountId(clearingAccount.getId())
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .currency(currency)
                        .build()
        );

        return postTransaction(referenceId, TransactionType.REFUND, currency, amount, description, instructions);
    }

    /**
     * Creates a chargeback posting:
     * DEBIT: Merchant Payable Account
     * CREDIT: Clearing Asset Account
     */
    @Transactional
    public LedgerTransactionEntity postChargeback(
            String referenceId,
            String merchantId,
            BigDecimal amount,
            Currency currency,
            String description
    ) {
        AccountEntity clearingAccount = accountService.getOrCreateAccount("SYSTEM_CLEARING", AccountType.CLEARING, currency);
        AccountEntity merchantAccount = accountService.getOrCreateAccount(merchantId, AccountType.MERCHANT_PAYABLE, currency);

        List<EntryInstruction> instructions = List.of(
                EntryInstruction.builder()
                        .accountId(merchantAccount.getId())
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .currency(currency)
                        .build(),
                EntryInstruction.builder()
                        .accountId(clearingAccount.getId())
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .currency(currency)
                        .build()
        );

        return postTransaction(referenceId, TransactionType.CHARGEBACK, currency, amount, description, instructions);
    }

    /**
     * Compensating Reversal Transaction:
     * Financial history is NEVER mutated. To reverse a transaction, we post an exact opposite compensating entry.
     */
    @Transactional
    public LedgerTransactionEntity postCompensatingReversal(String originalTransactionId, String reason) {
        log.info("Creating compensating reversal for original ledger transaction: {}", originalTransactionId);

        LedgerTransactionEntity originalTx = ledgerTransactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Original transaction not found: " + originalTransactionId));

        List<LedgerEntryEntity> originalEntries = ledgerEntryRepository.findByLedgerTransactionId(originalTransactionId);
        if (originalEntries.isEmpty()) {
            throw new IllegalStateException("Original transaction has no entries to reverse: " + originalTransactionId);
        }

        List<EntryInstruction> reverseInstructions = originalEntries.stream()
                .map(entry -> EntryInstruction.builder()
                        .accountId(entry.getAccount().getId())
                        .entryType(entry.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT)
                        .amount(entry.getAmount())
                        .currency(entry.getCurrency())
                        .build())
                .collect(Collectors.toList());

        String reversalRef = "REV-" + originalTx.getReferenceId() + "-" + UUID.randomUUID().toString().substring(0, 6);
        String desc = "Compensating reversal for tx " + originalTransactionId + ": " + (reason != null ? reason : "Correction");

        return postTransaction(
                reversalRef,
                TransactionType.REVERSAL,
                originalTx.getCurrency(),
                originalTx.getAmount(),
                desc,
                reverseInstructions
        );
    }

    @Transactional(readOnly = true)
    public LedgerTransactionResponse getTransactionResponse(String transactionId) {
        LedgerTransactionEntity tx = ledgerTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByLedgerTransactionId(transactionId);
        return toTransactionResponse(tx, entries);
    }

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponse> getTransactionsByReference(String referenceId) {
        return ledgerTransactionRepository.findByReferenceId(referenceId)
                .stream()
                .map(tx -> {
                    List<LedgerEntryEntity> entries = ledgerEntryRepository.findByLedgerTransactionId(tx.getId());
                    return toTransactionResponse(tx, entries);
                })
                .collect(Collectors.toList());
    }

    public BigDecimal calculateDelta(AccountType type, EntryType entryType, BigDecimal amount) {
        boolean isDebit = entryType == EntryType.DEBIT;
        return switch (type) {
            case CLEARING, SETTLEMENT, CUSTOMER_RECEIVABLE -> isDebit
                    ? LedgerMathUtil.scale(amount)
                    : LedgerMathUtil.scale(amount.negate());
            case MERCHANT_PAYABLE, FEE_REVENUE, SUSPENSE, RESERVE -> isDebit
                    ? LedgerMathUtil.scale(amount.negate())
                    : LedgerMathUtil.scale(amount);
        };
    }

    /**
     * Core Accounting Balance Equation:
     * ASSET: CLEARING, SETTLEMENT, CUSTOMER_RECEIVABLE -> Debit increases (+), Credit decreases (-)
     * LIABILITY / REVENUE: MERCHANT_PAYABLE, FEE_REVENUE, SUSPENSE, RESERVE -> Credit increases (+), Debit decreases (-)
     */
    public BigDecimal calculateNewBalance(AccountType type, BigDecimal currentBalance, EntryType entryType, BigDecimal amount) {
        boolean isDebit = entryType == EntryType.DEBIT;
        return switch (type) {
            case CLEARING, SETTLEMENT, CUSTOMER_RECEIVABLE -> isDebit
                    ? LedgerMathUtil.scale(currentBalance.add(amount))
                    : LedgerMathUtil.scale(currentBalance.subtract(amount));
            case MERCHANT_PAYABLE, FEE_REVENUE, SUSPENSE, RESERVE -> isDebit
                    ? LedgerMathUtil.scale(currentBalance.subtract(amount))
                    : LedgerMathUtil.scale(currentBalance.add(amount));
        };
    }

    private LedgerTransactionResponse toTransactionResponse(LedgerTransactionEntity tx, List<LedgerEntryEntity> entries) {
        List<LedgerTransactionResponse.LedgerEntryResponse> entryDtos = entries.stream()
                .map(e -> LedgerTransactionResponse.LedgerEntryResponse.builder()
                        .id(e.getId())
                        .accountId(e.getAccount().getId())
                        .accountNumber(e.getAccount().getAccountNumber())
                        .entryType(e.getEntryType())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return LedgerTransactionResponse.builder()
                .id(tx.getId())
                .referenceId(tx.getReferenceId())
                .transactionType(tx.getTransactionType())
                .currency(tx.getCurrency())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .status(tx.getStatus())
                .createdAt(tx.getCreatedAt())
                .entries(entryDtos)
                .build();
    }
}
