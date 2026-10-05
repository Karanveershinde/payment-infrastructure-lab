package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.EntryType;
import com.ledgerx.common.domain.enums.LedgerTransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponse {
    private String id;
    private String referenceId;
    private TransactionType transactionType;
    private Currency currency;
    private BigDecimal amount;
    private String description;
    private LedgerTransactionStatus status;
    private Instant createdAt;
    private List<LedgerEntryResponse> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntryResponse {
        private String id;
        private String accountId;
        private String accountNumber;
        private EntryType entryType;
        private BigDecimal amount;
        private Currency currency;
        private Instant createdAt;
    }
}
