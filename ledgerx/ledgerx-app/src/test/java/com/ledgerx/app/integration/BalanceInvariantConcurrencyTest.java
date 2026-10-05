package com.ledgerx.app.integration;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.service.TransactionOrchestrationService;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.LedgerAccountResponse;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.common.exception.InsufficientFundsException;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BalanceInvariantConcurrencyTest {

    @Autowired
    private TransactionOrchestrationService transactionOrchestrationService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountService accountService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @DisplayName("Should succeed when account has sufficient balance and exact balance")
    void shouldSucceedOnSufficientAndExactBalance() {
        String merchantId = "merch_bal_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_bal_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Fund Merchant with $100.00
        IngestTransactionRequest paymentReq = IngestTransactionRequest.builder()
                .transactionId("tx_fund_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.0000"))
                .currency(Currency.USD)
                .build();

        TransactionResponse paymentResp = transactionOrchestrationService.ingestTransaction(paymentReq, "req-1");
        assertThat(paymentResp.getStatus()).isEqualTo(TransactionStatus.POSTED);

        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("100.0000");

        // 2. Partial debit of $40.00 (Sufficient balance)
        ledgerService.postRefund("ref_part_1", merchantId, new BigDecimal("40.0000"), Currency.USD, "Partial refund");
        accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("60.0000");

        // 3. Exact debit of remaining $60.00 (Exact balance)
        ledgerService.postRefund("ref_exact_2", merchantId, new BigDecimal("60.0000"), Currency.USD, "Exact balance refund");
        accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Should reject direct refund when balance is insufficient")
    void shouldRejectDirectRefundOnInsufficientBalance() {
        String merchantId = "merch_empty_" + UUID.randomUUID().toString().substring(0, 8);
        accountService.getOrCreateAccount(merchantId, com.ledgerx.common.domain.enums.AccountType.MERCHANT_PAYABLE, Currency.USD);

        assertThatThrownBy(() -> ledgerService.postRefund(
                "ref_fail_01",
                merchantId,
                new BigDecimal("50.0000"),
                Currency.USD,
                "Overdraft attempt"
        )).isInstanceOf(InsufficientFundsException.class)
          .hasMessageContaining("Insufficient funds");

        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Concurrency: Concurrent debits against finite balance never produce negative balance")
    void shouldNeverAllowNegativeBalanceUnderHighConcurrency() throws InterruptedException {
        String merchantId = "merch_race_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_race_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Initial funding of $100.00
        IngestTransactionRequest paymentReq = IngestTransactionRequest.builder()
                .transactionId("tx_race_fund_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.0000"))
                .currency(Currency.USD)
                .build();

        transactionOrchestrationService.ingestTransaction(paymentReq, "req-race-init");

        // 2. Launch 10 concurrent threads each attempting to debit $30.00 ($300.00 total requested against $100.00 balance)
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerService.postRefund(
                            "ref_race_" + idx + "_" + UUID.randomUUID().toString().substring(0, 6),
                            merchantId,
                            new BigDecimal("30.0000"),
                            Currency.USD,
                            "Concurrent refund " + idx
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly 3 debits should succeed (3 * 30 = 90), 7 must be rejected
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(rejectedCount.get()).isEqualTo(7);

        // Final balance must be exactly $10.00 (100 - 90), never negative
        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("10.0000");
    }
}
