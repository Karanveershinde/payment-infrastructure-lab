package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.EntryType;
import com.ledgerx.common.domain.enums.LedgerTransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.exception.LedgerInvariantException;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import com.ledgerx.ledger.domain.entity.LedgerEntryEntity;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.repository.AccountRepository;
import com.ledgerx.ledger.repository.LedgerEntryRepository;
import com.ledgerx.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private LedgerService ledgerService;

    private AccountEntity clearingAccount;
    private AccountEntity merchantAccount;

    @BeforeEach
    void setUp() {
        clearingAccount = AccountEntity.builder()
                .id("acc-clearing-1")
                .accountNumber("CLR-SYSTEM-USD-001")
                .entityId("SYSTEM_CLEARING")
                .accountType(AccountType.CLEARING)
                .currency(Currency.USD)
                .balance(LedgerMathUtil.scale(BigDecimal.ZERO))
                .status("ACTIVE")
                .build();

        merchantAccount = AccountEntity.builder()
                .id("acc-merchant-1")
                .accountNumber("PAY-MERCH1-USD-001")
                .entityId("MERCH_001")
                .accountType(AccountType.MERCHANT_PAYABLE)
                .currency(Currency.USD)
                .balance(LedgerMathUtil.scale(BigDecimal.ZERO))
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Should post balanced payment transaction and update account balances correctly")
    void shouldPostBalancedPayment() {
        when(accountService.getOrCreateAccount(eq("SYSTEM_CLEARING"), eq(AccountType.CLEARING), eq(Currency.USD)))
                .thenReturn(clearingAccount);
        when(accountService.getOrCreateAccount(eq("MERCH_001"), eq(AccountType.MERCHANT_PAYABLE), eq(Currency.USD)))
                .thenReturn(merchantAccount);
        when(accountService.getAccountById("acc-clearing-1")).thenReturn(clearingAccount);
        when(accountService.getAccountById("acc-merchant-1")).thenReturn(merchantAccount);
        when(ledgerTransactionRepository.save(any(LedgerTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(accountRepository.updateBalanceDelta(any(), any())).thenReturn(1);

        BigDecimal amount = new BigDecimal("150.7500");
        LedgerTransactionEntity result = ledgerService.postPayment(
                "TX-12345",
                "MERCH_001",
                amount,
                Currency.USD,
                "Order #12345 payment"
        );

        assertThat(result).isNotNull();
        assertThat(result.getReferenceId()).isEqualTo("TX-12345");
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(result.getAmount()).isEqualByComparingTo(amount);
        assertThat(result.getStatus()).isEqualTo(LedgerTransactionStatus.POSTED);

        verify(accountRepository).updateBalanceDelta(eq("acc-clearing-1"), eq(new BigDecimal("150.7500")));
        verify(accountRepository).updateBalanceDelta(eq("acc-merchant-1"), eq(new BigDecimal("150.7500")));
        verify(ledgerEntryRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when debit reduces balance below zero")
    void shouldRejectTransactionWhenAccountHasInsufficientFunds() {
        when(accountService.getOrCreateAccount(eq("SYSTEM_CLEARING"), eq(AccountType.CLEARING), eq(Currency.USD)))
                .thenReturn(clearingAccount);
        when(accountService.getOrCreateAccount(eq("MERCH_001"), eq(AccountType.MERCHANT_PAYABLE), eq(Currency.USD)))
                .thenReturn(merchantAccount);
        when(accountService.getAccountById("acc-merchant-1")).thenReturn(merchantAccount);
        when(ledgerTransactionRepository.save(any(LedgerTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // updateBalanceDelta returns 0 (condition balance + delta >= 0 failed in SQL)
        when(accountRepository.updateBalanceDelta(eq("acc-merchant-1"), any())).thenReturn(0);

        assertThatThrownBy(() -> ledgerService.postRefund(
                "TX-REFUND-FAIL",
                "MERCH_001",
                new BigDecimal("50.0000"),
                Currency.USD,
                "Refund without balance"
        )).isInstanceOf(com.ledgerx.common.exception.InsufficientFundsException.class)
          .hasMessageContaining("Insufficient funds on account");
    }

    @Test
    @DisplayName("Should throw LedgerInvariantException when debits do not equal credits")
    void shouldFailWhenUnbalanced() {
        List<LedgerService.EntryInstruction> unbalancedInstructions = List.of(
                LedgerService.EntryInstruction.builder()
                        .accountId("acc-clearing-1")
                        .entryType(EntryType.DEBIT)
                        .amount(new BigDecimal("100.0000"))
                        .currency(Currency.USD)
                        .build(),
                LedgerService.EntryInstruction.builder()
                        .accountId("acc-merchant-1")
                        .entryType(EntryType.CREDIT)
                        .amount(new BigDecimal("99.0000")) // Discrepancy!
                        .currency(Currency.USD)
                        .build()
        );

        assertThatThrownBy(() -> ledgerService.postTransaction(
                "TX-FAIL",
                TransactionType.PAYMENT,
                Currency.USD,
                new BigDecimal("100.00"),
                "Unbalanced transaction",
                unbalancedInstructions
        )).isInstanceOf(LedgerInvariantException.class)
          .hasMessageContaining("Double-entry ledger invariant violation");

        verifyNoInteractions(ledgerTransactionRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    @DisplayName("Should post compensating reversal without mutating original transaction")
    void shouldPostCompensatingReversal() {
        String originalTxId = "tx-orig-001";
        LedgerTransactionEntity originalTx = LedgerTransactionEntity.builder()
                .id(originalTxId)
                .referenceId("TX-100")
                .transactionType(TransactionType.PAYMENT)
                .currency(Currency.USD)
                .amount(new BigDecimal("200.0000"))
                .status(LedgerTransactionStatus.POSTED)
                .build();

        LedgerEntryEntity entry1 = LedgerEntryEntity.builder()
                .id("entry-1")
                .ledgerTransaction(originalTx)
                .account(clearingAccount)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("200.0000"))
                .currency(Currency.USD)
                .build();

        LedgerEntryEntity entry2 = LedgerEntryEntity.builder()
                .id("entry-2")
                .ledgerTransaction(originalTx)
                .account(merchantAccount)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("200.0000"))
                .currency(Currency.USD)
                .build();

        // Initial balances after original transaction
        clearingAccount.setBalance(new BigDecimal("200.0000"));
        merchantAccount.setBalance(new BigDecimal("200.0000"));

        when(ledgerTransactionRepository.findById(originalTxId)).thenReturn(Optional.of(originalTx));
        when(ledgerEntryRepository.findByLedgerTransactionId(originalTxId)).thenReturn(List.of(entry1, entry2));
        when(accountService.getAccountById("acc-clearing-1")).thenReturn(clearingAccount);
        when(accountService.getAccountById("acc-merchant-1")).thenReturn(merchantAccount);
        when(accountRepository.updateBalanceDelta(any(), any())).thenReturn(1);
        when(ledgerTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerTransactionEntity reversalTx = ledgerService.postCompensatingReversal(originalTxId, "Wrong customer charge");

        assertThat(reversalTx).isNotNull();
        assertThat(reversalTx.getTransactionType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversalTx.getReferenceId()).startsWith("REV-TX-100");

        // Verify compensating entry updates invoked on accounts
        verify(accountRepository).updateBalanceDelta(eq("acc-clearing-1"), eq(new BigDecimal("-200.0000")));
        verify(accountRepository).updateBalanceDelta(eq("acc-merchant-1"), eq(new BigDecimal("-200.0000")));

        // Verify original transaction was never modified or deleted
        verify(ledgerTransactionRepository, never()).delete(any());
        verify(ledgerEntryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should test accounting balance calculation formula across all account types")
    void shouldCalculateBalancesCorrectly() {
        BigDecimal initial = new BigDecimal("100.0000");
        BigDecimal delta = new BigDecimal("25.0000");

        // ASSET (CLEARING)
        assertThat(ledgerService.calculateNewBalance(AccountType.CLEARING, initial, EntryType.DEBIT, delta))
                .isEqualByComparingTo("125.0000");
        assertThat(ledgerService.calculateNewBalance(AccountType.CLEARING, initial, EntryType.CREDIT, delta))
                .isEqualByComparingTo("75.0000");

        // LIABILITY (MERCHANT_PAYABLE)
        assertThat(ledgerService.calculateNewBalance(AccountType.MERCHANT_PAYABLE, initial, EntryType.CREDIT, delta))
                .isEqualByComparingTo("125.0000");
        assertThat(ledgerService.calculateNewBalance(AccountType.MERCHANT_PAYABLE, initial, EntryType.DEBIT, delta))
                .isEqualByComparingTo("75.0000");
    }
}
