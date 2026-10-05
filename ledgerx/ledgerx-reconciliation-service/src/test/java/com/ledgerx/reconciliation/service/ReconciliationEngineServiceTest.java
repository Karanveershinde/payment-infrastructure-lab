package com.ledgerx.reconciliation.service;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.ProviderSettlementItemDto;
import com.ledgerx.common.dto.request.RunReconciliationRequest;
import com.ledgerx.common.dto.response.ReconciliationBatchResponse;
import com.ledgerx.ledger.domain.entity.TransactionEntity;
import com.ledgerx.ledger.repository.TransactionRepository;
import com.ledgerx.reconciliation.domain.entity.ReconciliationBatchEntity;
import com.ledgerx.reconciliation.domain.entity.ReconciliationDiscrepancyEntity;
import com.ledgerx.reconciliation.repository.ProviderSettlementRecordRepository;
import com.ledgerx.reconciliation.repository.ReconciliationBatchRepository;
import com.ledgerx.reconciliation.repository.ReconciliationDiscrepancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationEngineServiceTest {

    @Mock
    private ReconciliationBatchRepository batchRepository;

    @Mock
    private ProviderSettlementRecordRepository settlementRecordRepository;

    @Mock
    private ReconciliationDiscrepancyRepository discrepancyRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ReconciliationEngineService reconciliationEngineService;

    private TransactionEntity tx1;
    private TransactionEntity tx2;

    @BeforeEach
    void setUp() {
        tx1 = TransactionEntity.builder()
                .id("tx-001")
                .provider(ProviderType.STRIPE)
                .providerTransactionId("ch_stripe_001")
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.0000"))
                .currency(Currency.USD)
                .status(TransactionStatus.POSTED)
                .createdAt(Instant.now())
                .build();

        tx2 = TransactionEntity.builder()
                .id("tx-002")
                .provider(ProviderType.STRIPE)
                .providerTransactionId("ch_stripe_002")
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("250.0000"))
                .currency(Currency.USD)
                .status(TransactionStatus.POSTED)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should reconcile perfectly matching settlement items")
    void shouldReconcileMatchingItems() {
        when(batchRepository.save(any(ReconciliationBatchEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findById("tx-001")).thenReturn(Optional.of(tx1));
        when(transactionRepository.findById("tx-002")).thenReturn(Optional.of(tx2));

        List<ProviderSettlementItemDto> items = List.of(
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_001")
                        .internalTransactionId("tx-001")
                        .amount(new BigDecimal("100.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .settlementTimestamp(Instant.now())
                        .build(),
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_002")
                        .internalTransactionId("tx-002")
                        .amount(new BigDecimal("250.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .settlementTimestamp(Instant.now())
                        .build()
        );

        RunReconciliationRequest request = RunReconciliationRequest.builder()
                .provider(ProviderType.STRIPE)
                .batchDate(LocalDate.now())
                .settlementRecords(items)
                .build();

        ReconciliationBatchResponse response = reconciliationEngineService.runReconciliation(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalRecords()).isEqualTo(2);
        assertThat(response.getMatchedRecords()).isEqualTo(2);
        assertThat(response.getDiscrepantRecords()).isEqualTo(0);
        verify(discrepancyRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should detect Amount Mismatch, Status Mismatch, Missing Internal, and Duplicate Provider records")
    @SuppressWarnings("unchecked")
    void shouldDetectMultipleDiscrepancies() {
        when(batchRepository.save(any(ReconciliationBatchEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // tx1: amount mismatch (internal=100.00, provider=80.00)
        when(transactionRepository.findById("tx-001")).thenReturn(Optional.of(tx1));
        // tx2: status mismatch (internal=SUCCESS, provider=FAILED)
        when(transactionRepository.findById("tx-002")).thenReturn(Optional.of(tx2));
        // unknown tx: missing internal
        when(transactionRepository.findById("tx-unknown")).thenReturn(Optional.empty());

        List<ProviderSettlementItemDto> items = List.of(
                // 1. Amount mismatch
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_001")
                        .internalTransactionId("tx-001")
                        .amount(new BigDecimal("80.0000")) // Mismatch!
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .build(),
                // 2. Status mismatch
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_002")
                        .internalTransactionId("tx-002")
                        .amount(new BigDecimal("250.0000"))
                        .currency(Currency.USD)
                        .status("FAILED") // Mismatch!
                        .build(),
                // 3. Missing Internal
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_phantom")
                        .internalTransactionId("tx-unknown")
                        .amount(new BigDecimal("50.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .build(),
                // 4. Duplicate Provider item
                ProviderSettlementItemDto.builder()
                        .providerTransactionId("ch_stripe_001") // Duplicate!
                        .internalTransactionId("tx-001")
                        .amount(new BigDecimal("80.0000"))
                        .currency(Currency.USD)
                        .status("SUCCESS")
                        .build()
        );

        RunReconciliationRequest request = RunReconciliationRequest.builder()
                .provider(ProviderType.STRIPE)
                .batchDate(LocalDate.now())
                .settlementRecords(items)
                .build();

        ReconciliationBatchResponse response = reconciliationEngineService.runReconciliation(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalRecords()).isEqualTo(4);
        assertThat(response.getMatchedRecords()).isEqualTo(0);
        assertThat(response.getDiscrepantRecords()).isEqualTo(4);

        ArgumentCaptor<List<ReconciliationDiscrepancyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(discrepancyRepository).saveAll(captor.capture());
        List<ReconciliationDiscrepancyEntity> savedDiscrepancies = captor.getValue();

        assertThat(savedDiscrepancies).hasSize(4);
        List<DiscrepancyType> types = savedDiscrepancies.stream()
                .map(ReconciliationDiscrepancyEntity::getDiscrepancyType)
                .toList();

        assertThat(types).contains(
                DiscrepancyType.AMOUNT_MISMATCH,
                DiscrepancyType.STATUS_MISMATCH,
                DiscrepancyType.MISSING_INTERNAL,
                DiscrepancyType.DUPLICATE_PROVIDER
        );
    }
}
