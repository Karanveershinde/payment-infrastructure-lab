package com.ledgerx.app.integration;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.service.EventReplayService;
import com.ledgerx.app.service.TransactionOrchestrationService;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.LedgerAccountResponse;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.IdempotencyService;
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
class IdempotencyAndReplayIntegrationTest {

    @Autowired
    private TransactionOrchestrationService transactionOrchestrationService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private EventReplayService eventReplayService;

    @Autowired
    private AccountService accountService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @DisplayName("Should test idempotency locks and prevent duplicate processing")
    void shouldPreventDuplicateProcessing() {
        String eventId = "evt_idem_" + UUID.randomUUID().toString().substring(0, 8);
        String consumer = "test-consumer";

        boolean firstAttempt = idempotencyService.tryAcquireProcessingLock(eventId, consumer);
        boolean secondAttempt = idempotencyService.tryAcquireProcessingLock(eventId, consumer);

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("Should replay transaction event safely without distorting ledger balances")
    void shouldReplayTransactionSafely() {
        String merchantId = "merch_rep_" + UUID.randomUUID().toString().substring(0, 6);
        String txId = "tx_rep_" + UUID.randomUUID().toString().substring(0, 6);
        String eventId = "evt_rep_" + UUID.randomUUID().toString().substring(0, 6);

        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .eventId(eventId)
                .transactionId(txId)
                .merchantId(merchantId)
                .customerId("cust_replay")
                .provider(ProviderType.CHECKOUT)
                .providerTransactionId("ch_rep_" + txId)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("300.0000"))
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_rep")
                .build();

        TransactionResponse txResponse = transactionOrchestrationService.ingestTransaction(request, "req-rep");
        assertThat(txResponse).isNotNull();

        // Check merchant balance after initial ingestion: 300.0000
        List<LedgerAccountResponse> accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("300.0000");

        // Execute Replay
        boolean replayed = eventReplayService.replayByEventId(eventId, "AUDITOR_ADMIN");
        assertThat(replayed).isTrue();

        // Check merchant balance remains exactly 300.0000 (no double-counting or distortion)
        accounts = accountService.getAccountsByEntity(merchantId);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("300.0000");
    }
}
