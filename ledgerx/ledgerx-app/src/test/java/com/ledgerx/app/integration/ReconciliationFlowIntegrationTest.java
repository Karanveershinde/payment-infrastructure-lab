package com.ledgerx.app.integration;

import com.ledgerx.app.messaging.KafkaEventProducer;
import com.ledgerx.app.service.TransactionOrchestrationService;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.request.ProviderSettlementItemDto;
import com.ledgerx.common.dto.request.RunReconciliationRequest;
import com.ledgerx.common.dto.response.DiscrepancyResponse;
import com.ledgerx.common.dto.response.ReconciliationBatchResponse;
import com.ledgerx.reconciliation.service.DiscrepancyManagementService;
import com.ledgerx.reconciliation.service.ReconciliationEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReconciliationFlowIntegrationTest {

    @Autowired
    private TransactionOrchestrationService transactionOrchestrationService;

    @Autowired
    private ReconciliationEngineService reconciliationEngineService;

    @Autowired
    private DiscrepancyManagementService discrepancyManagementService;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @DisplayName("Should ingest transactions and run reconciliation detecting matches and discrepancies")
    void shouldRunFullReconciliationWorkflow() {
        String merchantId = "merch_rec_" + UUID.randomUUID().toString().substring(0, 6);
        String txId1 = "tx_match_" + UUID.randomUUID().toString().substring(0, 6);
        String txId2 = "tx_diff_" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Ingest 2 internal transactions
        transactionOrchestrationService.ingestTransaction(
                IngestTransactionRequest.builder()
                        .transactionId(txId1)
                        .merchantId(merchantId)
                        .customerId("cust_1")
                        .provider(ProviderType.STRIPE)
                        .providerTransactionId("ch_prov_" + txId1)
                        .transactionType(TransactionType.PAYMENT)
                        .amount(new BigDecimal("100.0000"))
                        .currency(Currency.USD)
                        .paymentMethodToken("pm_card_1")
                        .build(),
                "req-1"
        );

        transactionOrchestrationService.ingestTransaction(
                IngestTransactionRequest.builder()
                        .transactionId(txId2)
                        .merchantId(merchantId)
                        .customerId("cust_2")
                        .provider(ProviderType.STRIPE)
                        .providerTransactionId("ch_prov_" + txId2)
                        .transactionType(TransactionType.PAYMENT)
                        .amount(new BigDecimal("200.0000"))
                        .currency(Currency.USD)
                        .paymentMethodToken("pm_card_2")
                        .build(),
                "req-2"
        );

        // 2. Build Provider Settlement Report with 1 Match and 1 Amount Mismatch
        List<ProviderSettlementItemDto> settlementItems = List.of(
                // Matching Item
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_prov_" + txId1)
                        .internalTransactionId(txId1)
                        .amount(new BigDecimal("100.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .settlementTimestamp(Instant.now())
                        .build(),
                // Discrepant Item (Amount mismatch: Internal $200.00 vs Provider $180.00)
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_prov_" + txId2)
                        .internalTransactionId(txId2)
                        .amount(new BigDecimal("180.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .settlementTimestamp(Instant.now())
                        .build()
        );

        RunReconciliationRequest reconRequest = RunReconciliationRequest.builder()
                .provider(ProviderType.STRIPE)
                .batchDate(LocalDate.now())
                .settlementRecords(settlementItems)
                .build();

        ReconciliationBatchResponse batchResponse = reconciliationEngineService.runReconciliation(reconRequest);

        assertThat(batchResponse).isNotNull();
        assertThat(batchResponse.getTotalRecords()).isEqualTo(2);
        assertThat(batchResponse.getMatchedRecords()).isEqualTo(1);
        assertThat(batchResponse.getDiscrepantRecords()).isEqualTo(1);

        // 3. Verify Discrepancy Record Retrieval
        Page<DiscrepancyResponse> discrepancies = discrepancyManagementService.getDiscrepanciesByBatch(batchResponse.getId(), Pageable.ofSize(10));
        assertThat(discrepancies.getContent()).hasSize(1);
        DiscrepancyResponse disc = discrepancies.getContent().get(0);
        assertThat(disc.getDiscrepancyType()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
        assertThat(disc.getInternalAmount()).isEqualByComparingTo("200.0000");
        assertThat(disc.getProviderAmount()).isEqualByComparingTo("180.0000");
    }
}
