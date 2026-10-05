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
import com.ledgerx.ledger.domain.entity.HeldEventEntity;
import com.ledgerx.ledger.repository.HeldEventRepository;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.HeldEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutOfOrderEventHandlingTest {

    @Autowired
    private TransactionOrchestrationService transactionOrchestrationService;

    @Autowired
    private HeldEventService heldEventService;

    @Autowired
    private HeldEventRepository heldEventRepository;

    @Autowired
    private AccountService accountService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @DisplayName("Out-of-Order: Refund arriving before Payment is held and automatically drained on Payment")
    void shouldHoldRefundBeforePaymentAndDrainOnPaymentArrival() {
        String merchantId = "merch_ooo_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_ooo_" + UUID.randomUUID().toString().substring(0, 8);
        String refundTxId = "tx_ooo_refund_" + UUID.randomUUID().toString().substring(0, 8);
        String paymentTxId = "tx_ooo_payment_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Ingest REFUND of $75.00 first (No payment has arrived yet)
        IngestTransactionRequest refundReq = IngestTransactionRequest.builder()
                .transactionId(refundTxId)
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.REFUND)
                .amount(new BigDecimal("75.0000"))
                .currency(Currency.USD)
                .build();

        TransactionResponse refundResp = transactionOrchestrationService.ingestTransaction(refundReq, "req-ooo-1");

        // Refund must be HELD without throwing unhandled error or creating negative balance
        assertThat(refundResp.getStatus()).isEqualTo(TransactionStatus.HELD);

        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("0.0000");

        List<HeldEventEntity> heldEvents = heldEventService.getPendingHeldEventsForMerchant(merchantId);
        assertThat(heldEvents).hasSize(1);
        assertThat(heldEvents.get(0).getTransactionId()).isEqualTo(refundTxId);
        assertThat(heldEvents.get(0).getStatus()).isEqualTo("HELD");

        // 2. Ingest Prerequisite PAYMENT of $100.00
        IngestTransactionRequest paymentReq = IngestTransactionRequest.builder()
                .transactionId(paymentTxId)
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.0000"))
                .currency(Currency.USD)
                .build();

        TransactionResponse paymentResp = transactionOrchestrationService.ingestTransaction(paymentReq, "req-ooo-2");
        assertThat(paymentResp.getStatus()).isEqualTo(TransactionStatus.POSTED);

        // 3. Held refund should have been automatically processed upon payment arrival
        heldEvents = heldEventRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, "PROCESSED");
        assertThat(heldEvents).hasSize(1);
        assertThat(heldEvents.get(0).getStatus()).isEqualTo("PROCESSED");

        // 4. Final Balance check: 100.00 (payment) - 75.00 (refund) = 25.00
        accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("25.0000");

        // Clearing account balance: 100.00 (debit) - 75.00 (credit) = 25.00
        List<LedgerAccountResponse> clearingAccounts = accountService.getAccountsByEntity("SYSTEM_CLEARING");
        assertThat(clearingAccounts.get(0).getBalance()).isGreaterThanOrEqualTo(new BigDecimal("25.0000"));
    }

    @Test
    @DisplayName("Normal flow: Payment followed by Partial Refund")
    void shouldExecuteNormalPaymentAndRefund() {
        String merchantId = "merch_norm_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_norm_" + UUID.randomUUID().toString().substring(0, 8);

        // Payment $200.00
        IngestTransactionRequest payReq = IngestTransactionRequest.builder()
                .transactionId("tx_norm_pay_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("200.0000"))
                .currency(Currency.USD)
                .build();
        TransactionResponse payResp = transactionOrchestrationService.ingestTransaction(payReq, "req-np-1");
        assertThat(payResp.getStatus()).isEqualTo(TransactionStatus.POSTED);

        // Refund $50.00
        IngestTransactionRequest refReq = IngestTransactionRequest.builder()
                .transactionId("tx_norm_ref_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.REFUND)
                .amount(new BigDecimal("50.0000"))
                .currency(Currency.USD)
                .build();
        TransactionResponse refResp = transactionOrchestrationService.ingestTransaction(refReq, "req-np-2");
        assertThat(refResp.getStatus()).isEqualTo(TransactionStatus.POSTED);

        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("Idempotency: Duplicate refund attempts return existing record without double deduction")
    void shouldHandleDuplicateRefundIdempotently() {
        String merchantId = "merch_dup_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_dup_" + UUID.randomUUID().toString().substring(0, 8);
        String txId = "tx_dup_ref_" + UUID.randomUUID().toString().substring(0, 8);

        // Fund merchant with $100.00
        IngestTransactionRequest payReq = IngestTransactionRequest.builder()
                .transactionId("tx_dup_fund_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.0000"))
                .currency(Currency.USD)
                .build();
        transactionOrchestrationService.ingestTransaction(payReq, "req-dup-1");

        // First refund attempt of $30.00
        IngestTransactionRequest refReq = IngestTransactionRequest.builder()
                .transactionId(txId)
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.REFUND)
                .amount(new BigDecimal("30.0000"))
                .currency(Currency.USD)
                .build();

        TransactionResponse ref1 = transactionOrchestrationService.ingestTransaction(refReq, "req-dup-2");
        assertThat(ref1.getStatus()).isEqualTo(TransactionStatus.POSTED);

        // Re-send exact same refund transaction
        TransactionResponse ref2 = transactionOrchestrationService.ingestTransaction(refReq, "req-dup-3");
        assertThat(ref2.getId()).isEqualTo(txId);

        // Merchant balance must be 70.00 (not 40.00)
        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("70.0000");
    }
}
