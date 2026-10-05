package com.ledgerx.app.controller;

import com.ledgerx.common.dto.response.LedgerAccountResponse;
import com.ledgerx.common.dto.response.LedgerTransactionResponse;
import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import com.ledgerx.ledger.service.AccountService;
import com.ledgerx.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Double-Entry Ledger", description = "Endpoints for inspecting immutable double-entry ledger accounts, balances, entries, and posting compensating reversals")
public class LedgerController {

    private final LedgerService ledgerService;
    private final AccountService accountService;

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get account details and balance", description = "Retrieves ledger account balance, account type, currency, and version.")
    public ResponseEntity<LedgerAccountResponse> getAccount(@PathVariable("accountId") String accountId) {
        return ResponseEntity.ok(accountService.getAccountResponse(accountId));
    }

    @GetMapping("/accounts/entity/{entityId}")
    @Operation(summary = "Get accounts for entity", description = "Lists all ledger accounts associated with a merchant or system entity ID.")
    public ResponseEntity<List<LedgerAccountResponse>> getAccountsByEntity(@PathVariable("entityId") String entityId) {
        return ResponseEntity.ok(accountService.getAccountsByEntity(entityId));
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get ledger transaction details", description = "Retrieves ledger transaction and all balanced debit and credit entries.")
    public ResponseEntity<LedgerTransactionResponse> getLedgerTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(ledgerService.getTransactionResponse(id));
    }

    @GetMapping("/transactions/reference/{referenceId}")
    @Operation(summary = "Get ledger transactions by reference ID", description = "Lists all ledger transactions posted under a given reference ID.")
    public ResponseEntity<List<LedgerTransactionResponse>> getByReference(@PathVariable("referenceId") String referenceId) {
        return ResponseEntity.ok(ledgerService.getTransactionsByReference(referenceId));
    }

    @PostMapping("/reversals")
    @Operation(summary = "Post compensating reversal transaction", description = "Creates an exact opposite compensating double-entry transaction without mutating historical records.")
    public ResponseEntity<LedgerTransactionResponse> postReversal(@RequestBody ReversalRequest request) {
        LedgerTransactionEntity reversalTx = ledgerService.postCompensatingReversal(
                request.getOriginalTransactionId(),
                request.getReason()
        );
        return ResponseEntity.ok(ledgerService.getTransactionResponse(reversalTx.getId()));
    }

    @Data
    public static class ReversalRequest {
        private String originalTransactionId;
        private String reason;
    }
}
