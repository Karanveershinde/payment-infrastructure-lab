package com.ledgerx.app.controller;

import com.ledgerx.common.dto.request.ProviderSettlementItemDto;
import com.ledgerx.common.dto.request.ResolveDiscrepancyRequest;
import com.ledgerx.common.dto.request.RunReconciliationRequest;
import com.ledgerx.common.dto.request.SyntheticBatchRequest;
import com.ledgerx.common.dto.response.DiscrepancyResponse;
import com.ledgerx.common.dto.response.ReconciliationBatchResponse;
import com.ledgerx.reconciliation.service.DiscrepancyManagementService;
import com.ledgerx.reconciliation.service.ReconciliationEngineService;
import com.ledgerx.reconciliation.service.SyntheticSettlementGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation Engine", description = "Endpoints for running external settlement report reconciliation, generating synthetic batches with anomalies, and resolving discrepancies")
public class ReconciliationController {

    private final ReconciliationEngineService reconciliationEngineService;
    private final SyntheticSettlementGeneratorService syntheticSettlementGeneratorService;
    private final DiscrepancyManagementService discrepancyManagementService;

    @PostMapping("/run")
    @Operation(summary = "Run reconciliation batch", description = "Reconciles a list of provider settlement line items against internal transactions.")
    public ResponseEntity<ReconciliationBatchResponse> runReconciliation(@Valid @RequestBody RunReconciliationRequest request) {
        return ResponseEntity.ok(reconciliationEngineService.runReconciliation(request));
    }

    @PostMapping({"/synthetic", "/run-synthetic"})
    @Operation(summary = "Generate and run synthetic reconciliation batch", description = "Generates synthetic provider settlement data with configurable anomaly rates and runs reconciliation.")
    public ResponseEntity<ReconciliationBatchResponse> runSyntheticBatch(@RequestBody SyntheticBatchRequest request) {
        List<ProviderSettlementItemDto> items = syntheticSettlementGeneratorService.generateSettlementReport(request);
        RunReconciliationRequest runReq = RunReconciliationRequest.builder()
                .provider(request.getProvider())
                .batchDate(LocalDate.now())
                .settlementRecords(items)
                .build();

        return ResponseEntity.ok(reconciliationEngineService.runReconciliation(runReq));
    }

    @GetMapping("/batches")
    @Operation(summary = "List reconciliation batches", description = "Retrieves paginated list of historical reconciliation runs.")
    public ResponseEntity<Page<ReconciliationBatchResponse>> listBatches(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(discrepancyManagementService.getAllBatches(pageable));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Get reconciliation batch details", description = "Retrieves batch statistics, match counts, and duration.")
    public ResponseEntity<ReconciliationBatchResponse> getBatch(@PathVariable("batchId") String batchId) {
        return ResponseEntity.ok(discrepancyManagementService.getBatchById(batchId));
    }

    @GetMapping("/{batchId}/discrepancies")
    @Operation(summary = "Get batch discrepancies", description = "Retrieves paginated list of categorized discrepancies for a specific batch.")
    public ResponseEntity<Page<DiscrepancyResponse>> getDiscrepancies(
            @PathVariable("batchId") String batchId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(discrepancyManagementService.getDiscrepanciesByBatch(batchId, pageable));
    }

    @PostMapping("/discrepancies/{discrepancyId}/resolve")
    @Operation(summary = "Resolve a discrepancy", description = "Marks a discrepancy as resolved with audit notes and operator identity.")
    public ResponseEntity<DiscrepancyResponse> resolveDiscrepancy(
            @PathVariable("discrepancyId") String discrepancyId,
            @Valid @RequestBody ResolveDiscrepancyRequest request
    ) {
        return ResponseEntity.ok(discrepancyManagementService.resolveDiscrepancy(discrepancyId, request));
    }
}
