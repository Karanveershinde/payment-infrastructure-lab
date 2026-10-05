package com.ledgerx.app.controller;

import com.ledgerx.app.service.TransactionOrchestrationService;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.TransactionResponse;
import com.ledgerx.ledger.service.TransactionStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Ingestion", description = "Endpoints for ingesting synthetic payment events, validating rules, and retrieving transaction state")
public class TransactionIngestionController {

    private final TransactionOrchestrationService transactionOrchestrationService;
    private final TransactionStorageService transactionStorageService;

    @PostMapping
    @Operation(summary = "Ingest a payment transaction event", description = "Validates event payload, runs risk engine evaluation, posts double-entry ledger entries, and persists immutable audit trail.")
    public ResponseEntity<TransactionResponse> ingestTransaction(
            @Valid @RequestBody IngestTransactionRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        TransactionResponse response = transactionOrchestrationService.ingestTransaction(request, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves stored internal transaction record including current status, risk evaluation, and reconciliation state.")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(transactionStorageService.getTransactionResponse(id));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get transactions for merchant", description = "Lists all transactions for the specified merchant ID.")
    public ResponseEntity<List<TransactionResponse>> getMerchantTransactions(@PathVariable("merchantId") String merchantId) {
        return ResponseEntity.ok(transactionStorageService.getTransactionsByMerchant(merchantId));
    }
}
