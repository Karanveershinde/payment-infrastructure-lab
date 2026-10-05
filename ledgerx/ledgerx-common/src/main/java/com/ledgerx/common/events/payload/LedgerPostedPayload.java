package com.ledgerx.common.events.payload;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.EntryType;
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
public class LedgerPostedPayload {
    private String ledgerTransactionId;
    private String referenceId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private Instant postedAt;
    private List<LedgerEntryItem> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntryItem {
        private String entryId;
        private String accountId;
        private EntryType entryType;
        private BigDecimal amount;
    }
}
