package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.ReconciliationStatus;
import com.ledgerx.common.domain.enums.RiskLevel;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
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
public class TransactionResponse {
    private String id;
    private String eventId;
    private String merchantId;
    private String customerId;
    private ProviderType provider;
    private String providerTransactionId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private TransactionStatus status;
    private Integer riskScore;
    private RiskLevel riskLevel;
    private ReconciliationStatus reconciliationStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
