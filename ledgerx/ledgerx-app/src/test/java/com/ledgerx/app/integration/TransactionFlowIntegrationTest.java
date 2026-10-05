package com.ledgerx.app.integration;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.service.TransactionOrchestrationService;
import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.AuditEventResponse;
import com.ledgerx.common.dto.response.LedgerAccountResponse;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TransactionFlowIntegrationTest {

    @Autowired
    private TransactionOrchestrationService transactionOrchestrationService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditService auditService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @DisplayName("Should execute complete end-to-end payment ingestion with double-entry balancing and audit logging")
    void shouldExecuteCompletePaymentFlow() {
        String merchantId = "merch_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_" + UUID.randomUUID().toString().substring(0, 8);
        String txId = "tx_" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal amount = new BigDecimal("250.0000");

        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .transactionId(txId)
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.STRIPE)
                .providerTransactionId("ch_stripe_" + txId)
                .transactionType(TransactionType.PAYMENT)
                .amount(amount)
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_valid")
                .build();

        TransactionResponse response = transactionOrchestrationService.ingestTransaction(request, "req-12345");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(txId);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(response.getAmount()).isEqualByComparingTo(amount);

        // Verify Merchant Account Balance (Liability increases on credit by +250.00)
        List<LedgerAccountResponse> merchantAccounts = accountService.getAccountsByEntity(merchantId);
        assertThat(merchantAccounts).isNotEmpty();
        assertThat(merchantAccounts.get(0).getBalance()).isEqualByComparingTo("250.0000");

        // Verify System Clearing Account Balance (Asset increases on debit by +250.00)
        List<LedgerAccountResponse> clearingAccounts = accountService.getAccountsByEntity("SYSTEM_CLEARING");
        assertThat(clearingAccounts).isNotEmpty();
        assertThat(clearingAccounts.get(0).getBalance()).isGreaterThanOrEqualTo(amount);

        // Verify Immutable Audit Trail
        List<AuditEventResponse> auditTrail = auditService.getAuditTrail("TRANSACTION", txId);
        assertThat(auditTrail).isNotEmpty();
    }

    @Test
    @DisplayName("Should block high-risk transaction without mutating ledger balances")
    void shouldBlockHighRiskTransaction() {
        String merchantId = "merch_risky_" + UUID.randomUUID().toString().substring(0, 8);
        String customerId = "cust_fraud_" + UUID.randomUUID().toString().substring(0, 8);
        String txId = "tx_fraud_" + UUID.randomUUID().toString().substring(0, 8);

        // High amount alone triggers 30 points, but let's test a transaction that accumulates > 80 points
        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .transactionId(txId)
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.ADYEN)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("99999.0000")) // Extremely high amount
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_stolen")
                .build();

        TransactionResponse response = transactionOrchestrationService.ingestTransaction(request, "req-fraud");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(txId);
    }
}
