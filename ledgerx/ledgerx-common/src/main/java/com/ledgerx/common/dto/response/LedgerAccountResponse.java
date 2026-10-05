package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccountResponse {
    private String id;
    private String accountNumber;
    private String entityId;
    private AccountType accountType;
    private Currency currency;
    private BigDecimal balance;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
}
